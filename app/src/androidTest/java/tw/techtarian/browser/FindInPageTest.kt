package tw.techtarian.browser

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FindInPageTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private var originalTheme="system"
    private var originalBottom=false
    @Before fun prepare(){
        assumeTrue("Only the isolated QA app",ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{
            originalTheme=c.store.theme;originalBottom=c.store.addressAtBottom
            ui.activity.bookmarkSync.disconnect();c.closeFindInPage();c.sheet="";c.stopEye()
            c.closePrivateTabs();c.tabs.toList().forEach{c.closeTab(it.id)}
        }
        ui.waitUntil(15000){!c.privateSession.clearing}
    }
    @After fun cleanup(){ui.runOnIdle{c.closeFindInPage();c.store.addressAtBottom=originalBottom;c.store.theme=originalTheme;c.closePrivateTabs()}}
    private fun load(private:Boolean=false):BrowserTab {
        val tab=AtomicReference<BrowserTab>()
        ui.runOnIdle{if(private)assertTrue(c.privateSession.supported);tab.set(c.newTab(incognito=private)!!)}
        val html="""<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>頁內搜尋測試</title></head>
            <body style='margin:0;padding:20px;background:#f1eee7;color:#20352c;font:18px sans-serif'>
            <h2>看著網頁找文字</h2><p>SEARCHMARK 搜尋目標 第一筆</p>
            <div style='height:1000px'></div><p>SEARCHMARK 搜尋目標 第二筆</p>
            <div style='height:1000px'></div><p>SEARCHMARK 搜尋目標 第三筆</p>
            <div style='height:300px'></div><span style='display:none'>SEARCHMARK 搜尋目標</span>
            </body></html>"""
        ui.runOnUiThread{tab.get().web.loadDataWithBaseURL("https://page-find.invalid/fixture",html,"text/html","UTF-8",null)}
        ui.waitUntil(15000){tab.get().title=="頁內搜尋測試"&&eval(tab.get(),"document.readyState")=="\"complete\""}
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        return tab.get()
    }
    private fun openFromMenu(){
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:尋找頁面文字").performScrollTo().performClick()
        ui.onNodeWithTag("find-in-page-input").assertIsDisplayed()
        ui.onNodeWithTag("browser-panel").assertDoesNotExist()
    }
    private fun search(text:String,count:Int){
        ui.onNodeWithTag("find-in-page-input").performTextReplacement(text)
        ui.waitUntil(10000){c.pageFind.query==text&&!c.pageFind.searching&&c.pageFind.count==count}
    }
    @Test fun menuOpensAnInlineSearchWhileTheSamePageRemainsVisible(){
        val tab=load();val url=ui.runOnIdle{tab.web.url};openFromMenu()
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        ui.onNodeWithTag("find-in-page-input").assertIsFocused()
        val bar=ui.onNodeWithTag("find-in-page-bar").getUnclippedBoundsInRoot()
        val page=ui.onNodeWithTag("web-content").getUnclippedBoundsInRoot()
        assertTrue("The field must not cover the WebView",page.top>=bar.bottom)
        assertTrue("A real page viewport must remain visible with the keyboard",page.bottom-page.top>40.dp)
        ui.runOnIdle{assertSame(tab,c.active);assertEquals(url,tab.web.url)}
        search("搜尋目標",3)
        ui.onNodeWithTag("find-in-page-count").assertTextEquals("1/3")
        ui.onNodeWithTag("find-in-page-next").performClick()
        screenshot("find-in-page-light")
    }
    @Test fun nextPreviousAndWrapMoveNativeMatchesAndScrollThePage(){
        val tab=load();openFromMenu();search("搜尋目標",3)
        ui.onNodeWithTag("find-in-page-next").performClick()
        ui.waitUntil(10000){c.pageFind.ordinal==2&&tab.web.scrollY>300}
        ui.onNodeWithTag("find-in-page-next").performClick();ui.waitUntil(5000){c.pageFind.ordinal==3}
        ui.onNodeWithTag("find-in-page-next").performClick();ui.waitUntil(5000){c.pageFind.ordinal==1}
        ui.onNodeWithTag("find-in-page-previous").performClick();ui.waitUntil(5000){c.pageFind.ordinal==3}
        assertSame(tab,c.active)
    }
    @Test fun noMatchAndClearingHaveVisibleStatusAndDisabledArrows(){
        load();openFromMenu();search("SEARCHMARK",3);search("no-such-text",0)
        ui.onNodeWithTag("find-in-page-count").assertTextEquals("0/0")
        ui.onNodeWithTag("find-in-page-next").assertIsNotEnabled()
        ui.onNodeWithTag("find-in-page-previous").assertIsNotEnabled()
        search("",0);ui.onNodeWithTag("find-in-page-count").assertTextEquals("—")
        ui.onNodeWithTag("web-content").assertIsDisplayed()
    }
    @Test fun closeAndBackReturnToTheSamePageAndForgetSearch(){
        val tab=load();openFromMenu();search("SEARCHMARK",3)
        ui.onNodeWithTag("find-in-page-close").performClick()
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.runOnIdle{assertSame(tab,c.active);assertEquals("",c.pageFind.query);assertNull(c.pageFind.ownerId)}
        openFromMenu();search("SEARCHMARK",3)
        ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.runOnIdle{assertSame(tab,c.active);assertEquals("https://page-find.invalid/fixture",tab.url)}
    }
    @Test fun rapidEditingAndSwitchingTabsNeverReuseSearchState(){
        val first=load();openFromMenu()
        for(text in listOf("S","SEARCHMARK","no-such-text","搜尋目標"))ui.onNodeWithTag("find-in-page-input").performTextReplacement(text)
        ui.waitUntil(10000){!c.pageFind.searching&&c.pageFind.count==3}
        ui.runOnIdle{c.newTab(incognito=false)}
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.runOnIdle{assertNull(c.pageFind.ownerId);assertEquals("",c.pageFind.query);c.switchTab(first.id)}
        openFromMenu();ui.onNodeWithTag("find-in-page-count").assertTextEquals("—")
    }
    @Test fun documentNavigationAndClosingTheOwnerClearSearch(){
        val tab=load();openFromMenu();search("搜尋目標",3)
        // Same URL reload: do not rely only on a URL state change to clear the listener.
        ui.runOnUiThread{tab.web.webViewClient.onPageStarted(tab.web,tab.url,null)}
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.runOnIdle{assertNull(c.pageFind.ownerId);c.openFindInPage()}
        search("搜尋目標",3)
        ui.runOnIdle{c.closeTab(tab.id)}
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.runOnIdle{assertNull(c.pageFind.ownerId)}
    }
    @Test fun incognitoSearchWorksWithoutPersistentSearchHistory(){
        val tab=load(true)
        val history=c.store.history();val searches=c.store.searches();val savedTabs=c.store.tabs()
        openFromMenu();search("搜尋目標",3)
        ui.onNodeWithTag("find-in-page-next").performClick()
        ui.waitUntil(5000){c.pageFind.ordinal==2}
        ui.onNodeWithTag("find-in-page-close").performClick()
        ui.runOnIdle{
            assertTrue(tab.incognito);assertEquals(history,c.store.history());assertEquals(searches,c.store.searches())
            assertEquals(savedTabs,c.store.tabs());assertNull(c.pageFind.ownerId)
        }
    }
    @Test fun bottomAddressAndDarkThemeStillKeepPageVisible(){
        ui.runOnIdle{c.store.addressAtBottom=true;c.store.theme="dark"}
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        load();openFromMenu();search("搜尋目標",3)
        ui.onNodeWithTag("find-in-page-next").performClick()
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        ui.onNodeWithTag("browser-controls").assertIsDisplayed()
        screenshot("find-in-page-dark-bottom")
    }
    @Test fun narrowLargeTextDoesNotPushControlsOutOfTheSearchBar(){
        var scale by mutableStateOf(1f)
        val session=PageFindSession()
        session.open(99,object:PageFindTarget {
            override fun listen(callback:((Int,Int,Boolean)->Unit)?){ }
            override fun find(query:String){ }
            override fun next(forward:Boolean){ }
            override fun clear(){ }
        })
        ui.runOnUiThread{ui.activity.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
                MaterialTheme{Column(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding().imePadding()){
                    FindInPageBar(session){}
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("remaining-page"))
                }}
            }
        }}
        for(value in listOf(1f,2f)){
            ui.runOnIdle{scale=value}
            val bar=ui.onNodeWithTag("find-in-page-bar").getUnclippedBoundsInRoot()
            for(tag in listOf("find-in-page-input","find-in-page-previous","find-in-page-next","find-in-page-close")){
                val bounds=ui.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("$tag exceeds the bar",bounds.left>=bar.left&&bounds.right<=bar.right&&bounds.bottom<=bar.bottom)
            }
            ui.onNodeWithTag("find-in-page-input").performTextReplacement("很長的搜尋句子ABC".repeat(20))
            ui.onNodeWithTag("remaining-page").assertIsDisplayed()
        }
        screenshot("find-in-page-320dp-large-text")
    }
    private fun eval(tab:BrowserTab,js:String):String {
        val done=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(js){result.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    private fun screenshot(name:String){
        ui.waitForIdle()
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).bufferedReader().use{it.readText()}
        shell("mkdir -p /data/local/tmp/chengjing-ui");shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
}
