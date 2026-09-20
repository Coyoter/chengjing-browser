package tw.techtarian.browser

import android.app.DownloadManager
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CollectionsPrivacyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val origin="https://chengjing-privacy.example/"
    @Before fun isolate(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.closePrivateTabs();c.sheet="";c.tabs.toList().forEach{c.closeTab(it.id)}}
    }
    @After fun finish(){ui.runOnIdle{c.closePrivateTabs();c.sheet="";c.updatePrivacyWindow()}}
    private fun requirePrivateSupport(){
        ui.runOnIdle{assertTrue("The validation WebView must exercise profile isolation and deletion, not skip privacy tests",c.privateSession.supported)}
    }
    @Test fun menuExposesCollectionsWithoutChangingFirstLayer(){
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:下載").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("menu-row:刪除瀏覽資料").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("menu-row:新增無痕分頁").performScrollTo().assertIsDisplayed()
        screenshot("collections-menu")
    }
    @Test fun historyHasSearchAndIndividualRemoval(){
        ui.runOnIdle{c.store.visit("https://history.example/one","測試文章甲");c.store.visit("https://history.example/two","測試文章乙");c.revision++;c.sheet="history"}
        screenshot("history-list")
        ui.onNodeWithText("搜尋瀏覽記錄").performTextInput("文章甲")
        ui.onNodeWithText("測試文章甲").assertIsDisplayed()
        ui.onNodeWithText("測試文章乙").assertDoesNotExist()
        ui.onNodeWithContentDescription("移除此筆瀏覽記錄").performClick()
        ui.runOnIdle{assertFalse(c.store.history().any{it.first=="https://history.example/one"});assertTrue(c.store.history().any{it.first=="https://history.example/two"})}
    }
    @Test fun privateModeEitherIsIsolatedOrFailsClosed(){
        ui.runOnIdle{
            val supported=c.privateSession.supported
            val tab=c.newTab(incognito=true)
            if(supported){assertNotNull(tab);assertTrue(tab!!.incognito)}
            else {assertNull(tab);assertTrue(c.tabs.none{it.incognito});assertTrue(c.notice.contains("WebView"))}
        }
    }
    @Test fun privatePagesNeverEnterPersistentHistorySearchOrTabRecovery(){
        requirePrivateSupport()
        val privateTab=AtomicReference<BrowserTab>()
        val oldHistory=AtomicReference<List<Pair<String,String>>>()
        val oldSearch=AtomicReference<List<String>>()
        ui.runOnIdle{oldHistory.set(c.store.history());oldSearch.set(c.store.searches());privateTab.set(c.newTab(incognito=true)!!)}
        load(privateTab.get(),origin+"private-secret","私密標題")
        ui.runOnIdle{
            c.recordSearchFor(privateTab.get(),"private-secret-search","https://www.google.com/search?q=private-secret-search")
            c.persistTabs()
            assertEquals(oldHistory.get(),c.store.history());assertEquals(oldSearch.get(),c.store.searches())
            assertFalse(c.store.tabs().any{it.contains("private-secret")})
            c.openTabOverview();assertNull(privateTab.get().preview)
            assertTrue(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        }
        val normal=AtomicReference<BrowserTab>()
        ui.runOnIdle{normal.set(c.tabs.first{!it.incognito})}
        ui.onNodeWithTag("tab-group-false").performClick()
        ui.onNodeWithTag("tab-card-${normal.get().id}").assertExists()
        ui.onNodeWithTag("tab-card-${privateTab.get().id}").assertDoesNotExist()
        ui.onNodeWithTag("tab-group-true").performClick()
        ui.onNodeWithTag("tab-card-${normal.get().id}").assertDoesNotExist()
        ui.onNodeWithTag("tab-card-${privateTab.get().id}").assertExists()
    }
    @Test fun cookiesAndLocalStorageAreSeparateAndClosingPrivateKeepsNormalLogin(){
        requirePrivateSupport()
        val normal=AtomicReference<BrowserTab>();val privateTab=AtomicReference<BrowserTab>()
        val oldCookies=AtomicReference<CookieManager>()
        ui.runOnIdle{normal.set(c.newTab(incognito=false)!!)}
        load(normal.get(),origin,"一般測試")
        eval(normal.get(),"document.cookie='normalLogin=kept; path=/';localStorage.setItem('state','normal');true")
        ui.runOnIdle{privateTab.set(c.newTab(incognito=true)!!);oldCookies.set(c.cookiesFor(privateTab.get()))}
        load(privateTab.get(),origin,"無痕測試")
        assertEquals("false",eval(privateTab.get(),"document.cookie.includes('normalLogin')"))
        assertEquals("null",eval(privateTab.get(),"localStorage.getItem('state')"))
        eval(privateTab.get(),"document.cookie='privateLogin=temporary; path=/';localStorage.setItem('state','private');true")
        assertEquals("false",eval(normal.get(),"document.cookie.includes('privateLogin')"))
        assertEquals("\"normal\"",eval(normal.get(),"localStorage.getItem('state')"))
        ui.runOnIdle{assertTrue(oldCookies.get().getCookie(origin).orEmpty().contains("privateLogin"));c.closePrivateTabs()}
        ui.waitUntil(15000){!c.privateSession.clearing}
        // A fresh random profile alone does not prove cleanup: check the old cookie store too.
        ui.runOnIdle{assertFalse(oldCookies.get().getCookie(origin).orEmpty().contains("privateLogin"))}
        assertEquals("true",eval(normal.get(),"document.cookie.includes('normalLogin')"))
        assertEquals("\"normal\"",eval(normal.get(),"localStorage.getItem('state')"))
        ui.runOnIdle{privateTab.set(c.newTab(incognito=true)!!)}
        load(privateTab.get(),origin,"新的無痕工作階段")
        assertEquals("false",eval(privateTab.get(),"document.cookie.includes('privateLogin')"))
        assertEquals("null",eval(privateTab.get(),"localStorage.getItem('state')"))
        eval(normal.get(),"document.cookie='normalLogin=;Max-Age=0;path=/';localStorage.removeItem('state');true")
    }
    @Test fun tabOverviewHasPreviewsAndSeparatedGroups(){
        val first=AtomicReference<BrowserTab>();val second=AtomicReference<BrowserTab>()
        ui.runOnIdle{first.set(c.newTab(incognito=false)!!)}
        load(first.get(),"https://tabs.example/reading","閱讀，留給自己")
        ui.runOnIdle{second.set(c.newTab(incognito=false)!!)}
        load(second.get(),"https://tabs.example/ideas","讓每個想法有位置")
        ui.runOnIdle{c.openTabOverview();assertNotNull(second.get().preview)}
        ui.onNodeWithTag("tab-grid").assertIsDisplayed()
        ui.onNodeWithTag("tab-group-false").assertIsDisplayed()
        ui.onNodeWithTag("tab-group-true").assertIsDisplayed()
        screenshot("tabs-normal")
    }
    @Test fun downloadsShowAnActualSystemDownloadFromThisApp(){
        val server=ServerSocket(0).apply{soTimeout=30000}
        val bytes="ChengJing download regression".toByteArray()
        val worker=Thread{
            runCatching{server.accept().use{socket->
                val reader=socket.getInputStream().bufferedReader()
                while(true){if(reader.readLine().isNullOrEmpty())break}
                socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
            }}
        }.apply{isDaemon=true;start()}
        var id:Long?=null
        val manager=ui.activity.getSystemService(DownloadManager::class.java)
        try{
            ui.runOnIdle{id=ui.activity.imageDownloads.download("http://127.0.0.1:${server.localPort}/chengjing-qa.txt","http://127.0.0.1/","QA",cookieHeader=null,mimeHint="text/plain",imageOnly=false)}
            assertNotNull(id)
            ui.waitUntil(30000){BrowserDownloads(ui.activity).list().any{it.id==id&&it.status==DownloadManager.STATUS_SUCCESSFUL}}
            manager.openDownloadedFile(id!!).use{fd->ParcelFileDescriptor.AutoCloseInputStream(fd).use{assertArrayEquals(bytes,it.readBytes())}}
            ui.runOnIdle{c.sheet="downloads"}
            ui.onNodeWithTag("download-list").assertIsDisplayed()
            ui.waitUntil(5000){ui.onAllNodes(hasText("chengjing-qa",substring=true)).fetchSemanticsNodes().isNotEmpty()}
            screenshot("downloads-list")
        }finally{if(id!=null)manager.remove(id!!);server.close();worker.join(1000)}
    }
    private fun load(tab:BrowserTab,url:String,title:String){
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>$title</title></head><body style='background:#f1eee7;color:#20352c;padding:30px;font:18px sans-serif'><h1>$title</h1><p>留白，讓閱讀回到內容。</p><p>這是隔離測試頁面，不需要連上外部網站。</p></body></html>"
        ui.runOnUiThread{tab.web.loadDataWithBaseURL(url,html,"text/html","UTF-8",null)}
        ui.waitUntil(10000){eval(tab,"document.readyState") == "\"complete\""&&tab.title==title}
        ui.waitForIdle()
    }
    private fun eval(tab:BrowserTab,code:String):String{
        val done=CountDownLatch(1);val answer=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(code){answer.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return answer.get()
    }
    private fun screenshot(name:String){
        ui.waitForIdle()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
        shell("mkdir -p /data/local/tmp/chengjing-ui")
        shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
}
