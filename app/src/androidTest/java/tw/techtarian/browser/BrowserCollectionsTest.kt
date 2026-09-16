package tw.techtarian.browser

import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BrowserCollectionsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val downloadIds=mutableListOf<Long>()
    private val prefNames=mutableSetOf<String>()
    private val origin="https://privacy.example.test/"
    @Before fun isolate(){
        assumeTrue("Only isolated QA application",ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.closeGroup(true);c.closeGroup(false);c.sheet="";c.stopEye()}
    }
    @After fun cleanup(){
        if(!ui.activity.packageName.endsWith(".qa"))return
        val manager=ui.activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadIds.forEach{manager.remove(it)}
        ui.runOnIdle{c.closeGroup(true);c.sheet="";prefNames.forEach{ui.activity.deleteSharedPreferences(it)}}
    }
    @Test fun menuContainsAllEntriesAndFirstToolbarIsUnchanged(){
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:新增無痕分頁").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("menu-row:歷史記錄").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("menu-row:下載").performScrollTo().performClick()
        ui.onNodeWithTag("browser-panel").assertExists()
        ui.onNodeWithText("只顯示由澄境瀏覽器下載的項目").assertExists()
        ui.onNodeWithContentDescription("返回上一層").performClick()
        ui.onNodeWithTag("menu-row:歷史記錄").performScrollTo().performClick()
        ui.onNodeWithTag("history-search").assertIsDisplayed()
    }
    @Test fun oldHistoryMigratesWithoutInventedDatesAndPrivateWritesAreIgnored(){
        val name="collection-test-"+UUID.randomUUID()
        val context=object:ContextWrapper(ui.activity){
            override fun getSharedPreferences(n:String,mode:Int):SharedPreferences {prefNames.add(name+n);return baseContext.getSharedPreferences(name+n,mode)}
        }
        val prefs=context.getSharedPreferences("browser-v1",Context.MODE_PRIVATE)
        prefs.edit().putString("history","[{\"url\":\"https://example.com/old\",\"title\":\"舊記錄\"}]").commit()
        val history=BrowserHistory(context)
        assertEquals(0L,history.entries().single().visitedAt)
        history.visit("https://example.com/private","不應記錄",privateMode=true)
        assertEquals(1,history.entries().size)
        history.visit("https://example.com/new","新記錄",now=42)
        assertEquals(42L,history.entries().first().visitedAt)
        assertEquals("舊記錄",history.entries().last().title)
        history.remove("https://example.com/new")
        assertEquals("舊記錄",history.entries().single().title)
    }
    @Test fun tabGroupsAreSeparateAndPrivatePagesAreNeverPersisted(){
        if(!PrivateSession.supported()){
            ui.runOnIdle{val count=c.tabs.size;assertNull(c.newTab(incognito=true));assertEquals(count,c.tabs.size);assertTrue(c.notice.contains("更新"))}
            return
        }
        val normal=ui.runOnIdle{c.active!!}
        val privateTab=ui.runOnIdle{c.newTab(incognito=true)!!}
        load(privateTab,origin+"secret","私密測試")
        ui.runOnIdle{
            c.persistTabs();c.captureActivePreview()
            assertFalse(c.store.tabs().any{it.contains("secret")})
            assertFalse(c.store.history().any{it.first.contains("secret")})
            assertNull(privateTab.preview)
            assertTrue(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
            c.showTabs()
        }
        ui.onNodeWithTag("private-tab-group").assertIsSelected()
        ui.onNodeWithTag("tab-card:${privateTab.id}").assertExists()
        ui.onNodeWithTag("tab-card:${normal.id}").assertDoesNotExist()
        ui.onNodeWithTag("normal-tab-group").performClick()
        ui.onNodeWithTag("tab-card:${normal.id}").assertExists()
        ui.onNodeWithTag("tab-card:${privateTab.id}").assertDoesNotExist()
        ui.runOnIdle{c.switchTab(normal.id);c.closeTab(privateTab.id)}
        ui.waitUntil(10000){c.privateSession.cleaning==0}
        ui.runOnIdle{assertEquals("",c.privateSession.cleanupError);assertFalse(c.incognito)}
    }
    @Test fun cookiesAndLocalStorageAreIsolatedAndLastPrivateTabClearsSession(){
        assumeTrue("WebView provider must support isolated profiles and full cleanup",PrivateSession.supported())
        val normal=ui.runOnIdle{c.active!!}
        load(normal,origin,"一般")
        eval(normal,"localStorage.setItem('marker','normal');document.cookie='marker=normal; path=/'")
        val first=ui.runOnIdle{c.newTab(incognito=true)!!}
        load(first,origin,"無痕一")
        assertEquals("null",eval(first,"localStorage.getItem('marker')"))
        assertFalse(eval(first,"document.cookie").contains("normal"))
        eval(first,"localStorage.setItem('marker','private');document.cookie='marker=private; path=/'")
        val second=ui.runOnIdle{c.newTab(incognito=true)!!}
        load(second,origin,"無痕二")
        assertEquals("\"private\"",eval(second,"localStorage.getItem('marker')"))
        assertTrue(eval(second,"document.cookie").contains("private"))
        ui.runOnIdle{c.closeTab(first.id);c.closeTab(second.id)}
        ui.waitUntil(10000){c.privateSession.cleaning==0}
        assertEquals("\"normal\"",eval(normal,"localStorage.getItem('marker')"))
        assertTrue(eval(normal,"document.cookie").contains("normal"))
        val fresh=ui.runOnIdle{c.newTab(incognito=true)!!}
        load(fresh,origin,"全新無痕")
        assertEquals("null",eval(fresh,"localStorage.getItem('marker')"))
        assertFalse(eval(fresh,"document.cookie").contains("private"))
        ui.runOnIdle{assertEquals("",c.privateSession.cleanupError)}
    }
    @Test fun recreationDoesNotRestorePrivateTabs(){
        assumeTrue(PrivateSession.supported())
        ui.runOnIdle{c.newTab(incognito=true)}
        ui.activityRule.scenario.recreate()
        ui.runOnIdle{assertFalse(c.incognito);assertTrue(c.tabs.none{it.incognito})}
    }
    @Test fun actualSystemDownloadAppearsAndCanBeOpenedAndRemoved(){
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val thread=Thread{
            try{while(!server.isClosed){server.accept().use{socket->
                socket.soTimeout=10000
                val reader=socket.getInputStream().bufferedReader()
                while(true){val line=reader.readLine()?:break;if(line.isEmpty())break}
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\ntest".toByteArray())
                socket.getOutputStream().flush()
            }}}catch(_:Exception){}
        }.apply{isDaemon=true;start()}
        try{
            val id=ui.runOnIdle{ui.activity.imageDownloads.download("http://127.0.0.1:${server.localPort}/collection.txt","http://127.0.0.1/","QA",c.active)!!}
            downloadIds.add(id)
            val downloads=BrowserDownloads(ui.activity)
            ui.waitUntil(30000){downloads.entries().any{it.id==id&&it.complete}}
            val entry=downloads.entries().single{it.id==id}
            assertEquals(4L,entry.total)
            assertNotNull(downloads.openIntent(id))
            ui.runOnIdle{c.sheet="downloads"}
            ui.waitUntil(10000){ui.onAllNodesWithTag("download:$id").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("download:$id").assertIsDisplayed()
            downloads.remove(id)
            assertTrue(downloads.entries().none{it.id==id})
            downloadIds.remove(id)
        }finally{server.close();thread.join(1000)}
    }
    @Test fun normalTabsHaveReadableCardsAndCaptureOnlyMemoryPreviews(){
        val normal=ui.runOnIdle{c.active!!}
        load(normal,"https://example.com/reading","閱讀，依自己的節奏")
        ui.runOnIdle{c.showTabs();assertNotNull(normal.preview)}
        ui.onNodeWithTag("tab-card:${normal.id}").assertIsDisplayed()
        screenshot("library-tabs")
        ui.onNodeWithTag("tab-open:${normal.id}").performClick()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:下載").performScrollTo()
        screenshot("library-menu")
    }
    private fun load(tab:BrowserTab,url:String,title:String){
        ui.runOnUiThread{tab.web.loadDataWithBaseURL(url,"<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>$title</title></head><body style='padding:28px;font-family:sans-serif;background:#f1eee7;color:#1d2925'><h1>$title</h1><p>這是測試使用的合成網頁，不包含任何真實瀏覽資料。</p></body></html>","text/html","UTF-8",null)}
        ui.waitUntil(15000){eval(tab,"document.readyState")=="\"complete\""&&eval(tab,"document.title").contains(title)}
        ui.waitForIdle()
    }
    private fun eval(tab:BrowserTab,code:String):String{
        val latch=CountDownLatch(1);val answer=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(code){answer.set(it);latch.countDown()}}
        assertTrue(latch.await(5,TimeUnit.SECONDS));return answer.get()
    }
    private fun screenshot(name:String){
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
        ui.waitForIdle();shell("mkdir -p /data/local/tmp/chengjing-ui");shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
        assertTrue((shell("stat -c %s /data/local/tmp/chengjing-ui/$name.png").trim().toLongOrNull()?:0)>0)
    }
}
