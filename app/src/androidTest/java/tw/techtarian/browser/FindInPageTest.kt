package tw.techtarian.browser

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
    private val search get()=c.findInPage
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun isolate(){
        assumeTrue("Only the isolated QA package",ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{
            ui.activity.bookmarkSync.disconnect();search.close();c.prompts.cancel();c.sheet="";c.stopEye()
            c.closePrivateTabs();ui.activity.applyAppearance("light")
        }
        ui.waitUntil(15000){!c.privateSession.clearing}
    }
    @After fun cleanup(){ui.runOnIdle{search.close();c.sheet="";c.closePrivateTabs();ui.activity.applyAppearance("system");c.store.addressAtBottom=false}}
    private fun fixture(privateMode:Boolean=false):BrowserTab{
        val result=AtomicReference<BrowserTab>()
        ui.runOnIdle{result.set(c.newTab(incognito=privateMode)!!)}
        val tab=result.get()
        val html="""<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>頁內搜尋測試</title></head>
            <body style='margin:0;padding:20px;background:#f8f6f0;color:#20352c;font:20px sans-serif'>
            <h1>邊看網頁，邊找文字</h1><p id='first'>搜尋針甲 SearchNeedle</p>
            <div style='height:900px'></div><p id='second'>搜尋針甲 SearchNeedle</p>
            <div style='height:900px'></div><p id='third'>搜尋針甲 SearchNeedle</p><div style='height:300px'></div>
            </body></html>"""
        ui.runOnUiThread{tab.web.loadDataWithBaseURL("https://find-page.example/fixture",html,"text/html","UTF-8",null)}
        ui.waitUntil(15000){tab.title=="頁內搜尋測試"&&tab.pendingUrl.isEmpty()&&eval(tab,"document.readyState")=="\"complete\""}
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        return tab
    }
    private fun openThroughMenu(){
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:尋找頁面文字").performScrollTo().performClick()
        ui.onNodeWithTag("find-query").assertIsDisplayed()
        ui.onNodeWithTag("browser-panel").assertDoesNotExist()
    }
    private fun type(text:String){ui.onNodeWithTag("find-query").performTextReplacement(text)}
    private fun counted(expected:Int){ui.waitUntil(10000){search.query.isNotEmpty()&&!search.searching&&search.matches==expected}}
    private fun shot(name:String){
        ui.waitForIdle();device.executeShellCommand("mkdir -p /data/local/tmp/chengjing-ui")
        device.executeShellCommand("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
    private fun eval(tab:BrowserTab,code:String):String{
        val done=CountDownLatch(1);val answer=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(code){answer.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return answer.get()
    }

    @Test fun menuSearchKeepsSameWebViewVisibleAndReturnsRealNativeMatches(){
        val tab=fixture();val url=tab.url;val id=tab.id;val before=c.tabs.size
        openThroughMenu()
        ui.onNodeWithTag("find-query").assertIsFocused()
        type("搜尋針甲");counted(3)
        ui.onNodeWithTag("find-result").assertTextEquals("1 / 3")
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        ui.runOnIdle{assertSame(tab,c.active);assertEquals(id,c.activeId);assertEquals(url,tab.url);assertEquals(before,c.tabs.size);assertTrue(tab.web.isAttachedToWindow);assertTrue(tab.web.height>100)}
        shot("find-page-keyboard-visible")
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==2}
        ui.waitUntil(10000){eval(tab,"scrollY").toDoubleOrNull()?.let{it>100}==true}
        ui.onNodeWithTag("find-result").assertTextEquals("2 / 3")
        shot("find-page-next-visible")
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==3}
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==1}
        ui.onNodeWithTag("find-previous").performClick();ui.waitUntil(10000){search.current==3}
        ui.onNodeWithTag("find-close").performClick()
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.onNodeWithTag("browser-controls").assertIsDisplayed()
        ui.runOnIdle{assertSame(tab,c.active);assertEquals(url,tab.url);assertEquals("",search.query);assertEquals(0,search.matches)}
    }
    @Test fun noResultsEmptyQueryAndRapidEditsDoNotLeaveOldMatchesOrEnabledArrows(){
        val tab=fixture();openThroughMenu();type("SearchNeedle");counted(3)
        type("SearchNeedle-not-on-page");counted(0)
        ui.onNodeWithTag("find-result").assertTextEquals("找不到")
        ui.onNodeWithTag("find-next").assertIsNotEnabled();ui.onNodeWithTag("find-previous").assertIsNotEnabled()
        ui.onNodeWithTag("find-query").performTextClearance()
        ui.runOnIdle{assertEquals(0,search.matches);assertFalse(search.searching)}
        ui.runOnIdle{search.update("搜尋針");search.update("unmatched");search.update("SearchNeedle")}
        counted(3)
        ui.onNodeWithTag("find-result").assertTextEquals("1 / 3")
        ui.onNodeWithTag("find-close").performClick()
        ui.runOnIdle{search.move(true);assertSame(tab,c.active);assertFalse(search.isOpen)}
        openThroughMenu();ui.runOnIdle{assertEquals("",search.query)}
    }
    @Test fun backClosesSearchWithoutNavigatingOrCreatingATab(){
        val tab=fixture();openThroughMenu();type("SearchNeedle");counted(3)
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==2}
        val count=c.tabs.size
        device.pressBack()
        ui.onNodeWithTag("find-in-page-bar").assertDoesNotExist()
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        ui.runOnIdle{assertSame(tab,c.active);assertEquals(count,c.tabs.size)}
    }
    @Test fun switchingClosingReloadingAndOpeningOtherPanelsEndTheOldSearch(){
        val first=fixture();val second=fixture()
        openThroughMenu();type("SearchNeedle")
        ui.runOnIdle{c.switchTab(first.id)}
        ui.onNodeWithTag("find-query").assertDoesNotExist()
        ui.runOnIdle{assertFalse(search.isOpen);assertEquals("",search.query)}
        openThroughMenu();type("SearchNeedle");counted(3)
        ui.runOnIdle{c.sheet="history"}
        ui.onNodeWithTag("find-query").assertDoesNotExist()
        ui.runOnIdle{assertFalse(search.isOpen);c.sheet=""}
        openThroughMenu();type("SearchNeedle");counted(3)
        ui.runOnIdle{first.web.webViewClient.onPageStarted(first.web,first.url,null)}
        ui.onNodeWithTag("find-query").assertDoesNotExist()
        ui.runOnIdle{assertFalse(search.isOpen);c.switchTab(second.id)}
        openThroughMenu();type("SearchNeedle")
        ui.runOnIdle{c.closeTab(second.id)}
        ui.onNodeWithTag("find-query").assertDoesNotExist()
        ui.runOnIdle{assertFalse(search.isOpen)}
    }
    @Test fun privateSearchWorksWithoutWritingTheQueryToBrowserPreferences(){
        ui.runOnIdle{assertTrue(c.privateSession.supported)}
        val tab=fixture(true)
        val prefs=ui.activity.getSharedPreferences("browser-v1",android.content.Context.MODE_PRIVATE)
        val before=prefs.all.toMap()
        openThroughMenu();type("SearchNeedle");counted(3)
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==2}
        ui.onNodeWithTag("find-close").performClick()
        ui.runOnIdle{assertTrue(c.active!!.incognito);assertSame(tab,c.active);assertEquals(before,prefs.all);assertEquals("",search.query);assertNull(tab.preview)}
    }
    @Test fun bottomAddressPreferenceAndDarkThemeKeepThePageUnderTheSearchBar(){
        ui.runOnIdle{c.store.addressAtBottom=true;ui.activity.applyAppearance("dark")}
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        fixture();openThroughMenu();type("SearchNeedle");counted(3)
        ui.onNodeWithTag("find-next").performClick();ui.waitUntil(10000){search.current==2}
        val bar=ui.onNodeWithTag("find-in-page-bar").fetchSemanticsNode().boundsInRoot
        val page=ui.onNodeWithTag("web-content").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(bar.bottom<=page.top+1f);assertTrue(page.height>100f)
        shot("find-page-dark-bottom-address")
        ui.onNodeWithTag("find-close").performClick()
        ui.onNodeWithTag("browser-controls").assertIsDisplayed()
        ui.onNodeWithTag("browser-topbar").assertIsDisplayed()
    }
    @Test fun narrowSearchBarAndDoubleFontKeepInputAndControlsInsideBounds(){
        fixture()
        ui.runOnIdle{assertTrue(search.open())}
        var scale by mutableFloatStateOf(1f)
        ui.runOnUiThread{ui.activity.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
                MaterialTheme{Surface{Column(Modifier.width(320.dp)){FindInPageBar(search,requestKeyboard=false)}}}
            }
        }}
        for(value in listOf(1f,2f)){
            ui.runOnIdle{scale=value}
            val bar=ui.onNodeWithTag("find-in-page-bar").fetchSemanticsNode().boundsInRoot
            val field=ui.onNodeWithTag("find-query").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(field.width>100f)
            for(tag in listOf("find-query","find-result","find-next","find-previous","find-close")){
                val bounds=ui.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Out of bar: $tag at scale $value",bounds.left>=bar.left&&bounds.right<=bar.right+1&&bounds.top>=bar.top&&bounds.bottom<=bar.bottom+1)
            }
        }
        shot("find-page-320dp-double-font")
    }
}
