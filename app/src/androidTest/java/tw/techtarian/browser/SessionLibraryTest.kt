package tw.techtarian.browser

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SessionLibraryTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val url="https://session-fixture.example/reading"
    @Before fun isolate(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.stopEye();c.sheet="";c.newTab(incognito=false)}
    }
    @After fun endPrivateSessions(){ui.runOnIdle{c.tabs.filter{it.incognito}.map{it.id}.forEach{c.closeTab(it)};c.sheet=""}}
    @Test fun menuHasAllThreeToolsAndFirstLayerStaysAtFiveActions(){
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        listOf("下載","歷史記錄","新增無痕分頁").forEach{ui.onNodeWithTag("menu-row:$it").assertExists()}
        ui.onNodeWithTag("menu-row:歷史記錄").performScrollTo().performClick()
        ui.onNodeWithTag("history-search").assertIsDisplayed()
    }
    @Test fun browsingHistoryIsSearchableAndCanRemoveOneEntry(){
        val first="https://history-fixture.example/one"
        val second="https://history-fixture.example/two"
        ui.runOnIdle{c.store.visit(first,"第一筆測試閱讀");c.store.visit(second,"第二筆測試閱讀");c.revision++;c.sheet="history"}
        ui.onNodeWithTag("history-search").performTextInput("第一筆")
        ui.onNodeWithText("第一筆測試閱讀").assertIsDisplayed()
        ui.onNodeWithText("第二筆測試閱讀").assertDoesNotExist()
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
        snapshot("history")
        ui.onNodeWithContentDescription("移除這筆歷史記錄").performClick()
        ui.runOnIdle{assertFalse(c.store.history().any{it.first==first});assertTrue(c.store.history().any{it.first==second});c.store.removeHistory(second)}
    }
    @Test fun legacyHistoryEntriesRemainReadable(){
        val base=ui.activity
        val key="history-migration-test"
        val isolated=object:android.content.ContextWrapper(base){
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$key-$name",mode)
        }
        try{
            isolated.getSharedPreferences("browser-v1",Context.MODE_PRIVATE).edit().putString("history","[{\"url\":\"https://history.example/\",\"title\":\"舊紀錄\"}]").commit()
            val store=BrowserStore(isolated)
            assertEquals("舊紀錄",store.historyEntries().single().title)
            assertEquals(0L,store.historyEntries().single().visited)
            store.visit("https://new.example/","新紀錄")
            assertEquals(2,store.historyEntries().size)
        }finally{base.deleteSharedPreferences("$key-browser-v1");base.deleteSharedPreferences("$key-bookmarks-v1")}
    }
    @Test fun normalAndPrivateTabsHaveSeparateCookiesAndNoPrivateHistoryOrRestoration(){
        ui.runOnIdle{assertTrue("Test provider must support isolated profiles and browsing data deletion",c.privateSession.supported)}
        val normalCookie=CookieManager.getInstance()
        setCookie(normalCookie,"normal_only=yes; Path=/; Secure")
        val normal=ui.runOnIdle{c.active!!}
        ui.runOnIdle{c.newTab(incognito=true)!!;c.active!!.web.loadDataWithBaseURL(url,"<title>私密測試</title><p>private</p>","text/html","UTF-8",null)}
        waitPage("私密測試")
        val privateTab=ui.runOnIdle{c.active!!}
        val privateCookie=ui.runOnIdle{WebViewCompat.getProfile(privateTab.web).cookieManager}
        assertFalse(privateCookie.getCookie(url).orEmpty().contains("normal_only"))
        setCookie(privateCookie,"private_only=yes; Path=/; Secure")
        assertFalse(normalCookie.getCookie(url).orEmpty().contains("private_only"))
        ui.runOnIdle{
            c.persistTabs()
            assertFalse(c.store.tabs().any{it.contains("session-fixture")})
            assertFalse(c.store.history().any{it.first.contains("session-fixture")})
            assertNull(privateTab.preview)
            assertTrue(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
            c.switchTab(normal.id)
            assertEquals(normal.id,c.activeId)
            c.closeTab(privateTab.id)
        }
        ui.waitUntil(10000){runCatching{privateCookie.getCookie(url)}.getOrNull().orEmpty().isBlank()}
        assertTrue(normalCookie.getCookie(url).orEmpty().contains("normal_only"))
        ui.runOnIdle{c.newTab(incognito=true)}
        val fresh=ui.runOnIdle{WebViewCompat.getProfile(c.active!!.web).cookieManager}
        assertFalse(fresh.getCookie(url).orEmpty().contains("private_only"))
        setCookie(normalCookie,"normal_only=; Max-Age=0; Path=/; Secure")
    }
    @Test fun tabCollectionsUseSeparateCardsAndPrivateCardsNeverCaptureScreenshots(){
        ui.runOnIdle{assertTrue(c.privateSession.supported);c.active!!.web.loadDataWithBaseURL("https://cards.example/","<title>閱讀中的文章</title><body style='background:#eee'><h1>閱讀中的文章</h1></body>","text/html","UTF-8",null)}
        waitPage("閱讀中的文章")
        val normal=ui.runOnIdle{c.active!!}
        ui.runOnIdle{c.newTab(incognito=true)}
        val private=ui.runOnIdle{c.active!!}
        ui.runOnIdle{c.switchTab(normal.id);c.showTabs()}
        ui.onNodeWithTag("tabs-normal").assertIsSelected()
        ui.onNodeWithTag("tab-card:${normal.id}").assertExists()
        ui.onNodeWithTag("tab-card:${private.id}").assertDoesNotExist()
        snapshot("tabs-normal")
        ui.onNodeWithTag("tabs-private").performClick()
        ui.onNodeWithTag("tab-card:${private.id}").assertExists()
        ui.onNodeWithTag("tab-card:${normal.id}").assertDoesNotExist()
        ui.runOnIdle{assertNull(private.preview)}
        ui.onNodeWithTag("close-tab:${private.id}").performClick()
        ui.runOnIdle{assertTrue(c.tabs.any{it.id==normal.id});assertFalse(c.tabs.any{it.incognito})}
    }
    @Test fun systemDownloadsFromThisAppAppearInTheDownloadList(){
        val png=ByteArrayOutputStream().also{out->Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).let{it.compress(Bitmap.CompressFormat.PNG,100,out);it.recycle()}}.toByteArray()
        val server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"))
        val worker=Thread{
            runCatching{server.accept().use{socket->
                socket.soTimeout=10000
                val input=socket.getInputStream().bufferedReader()
                while(true){val line=input.readLine()?:break;if(line.isEmpty())break}
                socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${png.size}\r\nConnection: close\r\n\r\n".toByteArray());write(png);flush()}
            }}
        }.apply{isDaemon=true;start()}
        val manager=ui.activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var id:Long?=null
        try{
            id=ui.runOnIdle{ui.activity.imageDownloads.download("http://127.0.0.1:${server.localPort}/qa-download.png","http://127.0.0.1/","ChengJing-QA")}
            assertNotNull(id)
            val library=DownloadLibrary(ui.activity)
            ui.waitUntil(20000){library.list().any{it.id==id&&it.status==DownloadManager.STATUS_SUCCESSFUL}}
            ui.runOnIdle{c.sheet="downloads"}
            ui.onNodeWithTag("download-search").performTextInput("qa-download")
            ui.waitUntil(6000){ui.onAllNodes(hasTestTag("download:$id")).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("download:$id").assertIsDisplayed()
            androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
            snapshot("downloads")
        }finally{id?.let{manager.remove(it)};server.close();worker.join(1000)}
    }
    private fun setCookie(manager:CookieManager,cookie:String){
        val latch=CountDownLatch(1)
        ui.runOnUiThread{manager.setCookie(url,cookie){latch.countDown()}}
        assertTrue(latch.await(5,TimeUnit.SECONDS))
    }
    private fun waitPage(title:String){
        ui.waitUntil(15000){
            val value=AtomicReference("");val latch=CountDownLatch(1)
            ui.runOnUiThread{c.active!!.web.evaluateJavascript("document.readyState === 'complete' && document.title === "+org.json.JSONObject.quote(title)){value.set(it);latch.countDown()}}
            latch.await(3,TimeUnit.SECONDS)&&value.get()=="true"
        }
    }
    private fun snapshot(name:String){
        ui.waitForIdle()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
        shell("mkdir -p /data/local/tmp/chengjing-ui")
        shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
}
