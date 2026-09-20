package tw.techtarian.browser

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BrowsingDataTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    @Before fun isolate(){
        assumeTrue("Only the disposable QA package",ui.activity.packageName.endsWith(".qa"))
        f.reset();ui.runOnIdle{c.store.clearHistory()}
    }
    @After fun cleanup(){ui.runOnIdle{c.closePrivateTabs();c.sheet=""}}
    private fun clear(range:BrowsingTimeRange,selection:BrowsingDataSelection){
        ui.runOnIdle{c.browsingDataCleaner.clear(range,selection)}
        ui.waitUntil(20000){!c.browsingDataCleaner.running}
        ui.runOnIdle{assertEquals("",c.browsingDataCleaner.error)}
    }
    private fun eval(tab:BrowserTab,code:String):String{
        val done=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(code){result.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    @Test fun menuAndTimeRangesAreVisibleAndCancellingKeepsEverything(){
        val tab=f.load("https://deletion.example/read","留下閱讀")
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:歷史記錄").assertDoesNotExist()
        ui.onNodeWithTag("menu-shortcut:瀏覽記錄").performClick()
        ui.onNode(hasText("瀏覽記錄") and hasAnyAncestor(hasTestTag("panel-header"))).assertIsDisplayed()
        ui.onNodeWithContentDescription("返回上一層").performClick()
        ui.onNodeWithTag("menu-row:刪除瀏覽資料").performScrollTo().performClick()
        ui.onNodeWithTag("delete-site-data").assertIsOff()
        ui.onNodeWithTag("browsing-data-time-range").performClick()
        BrowsingTimeRange.entries.forEach{ui.onNodeWithTag("browsing-range-${it.name}").assertExists()}
        f.screenshot("delete-browsing-data-ranges")
        ui.onNodeWithTag("browsing-range-LAST_DAY").performClick()
        f.screenshot("delete-browsing-data-light")
        val ids=c.tabs.map{it.id};val records=c.store.historyPages()
        ui.onNodeWithText("取消").performClick()
        ui.runOnIdle{assertEquals(ids,c.tabs.map{it.id});assertEquals(records,c.store.historyPages());assertTrue(tab in c.tabs)}
    }
    @Test fun finiteDeletionPreservesOlderRecordsTabsPrivateDataAndSavedCollections(){
        val old=f.load("https://deletion.example/old","較早閱讀")
        val recent=f.load("https://deletion.example/recent","最近閱讀")
        ui.runOnIdle{assertTrue(c.privateSession.supported)}
        val private=f.load("https://deletion.example/private","私密閱讀",true)
        val now=System.currentTimeMillis();val yesterday=now-86_400_000
        ui.runOnIdle{
            c.store.clearHistory()
            c.store.visit(old.url,old.title,yesterday);c.store.visit(recent.url,recent.title,now)
            c.store.recordSearch("較早搜尋","https://www.google.com/search?q=old",yesterday)
            c.store.recordSearch("最近搜尋","https://www.google.com/search?q=recent",now)
            old.lastActiveAt=yesterday;recent.lastActiveAt=now
            c.store.bookmarkStore.add("https://deletion.example/bookmark","保留書籤")
            c.favorites.save("https://deletion.example/favorite","保留收藏",0.0,0.0)
            c.store.save(SiteRules("deletion.example",guard=true))
            c.persistTabs();c.previews.flush()
        }
        eval(old,"document.cookie='keepLogin=yes;path=/';localStorage.setItem('keep','yes');true")
        val bookmarks=c.store.bookmarks();val favorites=c.favorites.all()
        clear(BrowsingTimeRange.LAST_15_MINUTES,BrowsingDataSelection(true,true,false))
        ui.runOnIdle{
            assertEquals(listOf(old.url),c.store.history().map{it.first})
            assertEquals(listOf("較早搜尋"),c.store.searches())
            assertTrue(old in c.tabs);assertTrue(private in c.tabs);assertFalse(recent in c.tabs)
            assertEquals(bookmarks,c.store.bookmarks());assertEquals(favorites,c.favorites.all())
            assertTrue(c.store.get("deletion.example").guard)
            assertTrue(c.store.savedTabRecords().any{it.key==old.previewKey&&it.lastActiveAt==yesterday})
        }
        assertFalse(f.file(recent.previewKey!!).exists())
        assertEquals("true",eval(old,"document.cookie.includes('keepLogin=yes')"))
        assertEquals("\"yes\"",eval(old,"localStorage.getItem('keep')"))
        // A late completion from a destroyed tab cannot put a deleted URL back into history.
        ui.runOnIdle{recent.web.webViewClient.onPageFinished(recent.web,"https://deletion.example/recent")}
        assertFalse(c.store.history().any{it.first.contains("recent")})
    }
    @Test fun upgradingUndatedDataDoesNotInventTimesAndAllTimeRemovesIt(){
        val prefs=ui.activity.getSharedPreferences("browser-v1",Context.MODE_PRIVATE)
        ui.runOnIdle{
            prefs.edit().putString("history","""[{"url":"https://legacy.example/","title":"舊版資料"}]""")
                .putString("searches","""["舊版搜尋"]""").commit()
            c.tabs.forEach{it.lastActiveAt=0};c.persistTabs()
        }
        clear(BrowsingTimeRange.LAST_FOUR_WEEKS,BrowsingDataSelection(true,true,false))
        ui.runOnIdle{assertEquals(1,c.store.history().size);assertEquals(listOf("舊版搜尋"),c.store.searches());assertTrue(c.tabs.any{it.lastActiveAt==0L})}
        clear(BrowsingTimeRange.ALL_TIME,BrowsingDataSelection(true,true,false))
        ui.runOnIdle{assertTrue(c.store.history().isEmpty());assertTrue(c.store.searches().isEmpty());assertEquals(1,c.tabs.size);assertTrue(c.active!!.url.isEmpty());assertTrue(c.active!!.lastActiveAt>0)}
    }
    @Test fun historyOnlyLeavesTabsAndTabOnlyLeavesHistory(){
        val tab=f.load("https://deletion.example/independent","分開刪除")
        val ids=c.tabs.map{it.id}
        clear(BrowsingTimeRange.ALL_TIME,BrowsingDataSelection(true,false,false))
        ui.runOnIdle{assertTrue(c.store.history().isEmpty());assertEquals(ids,c.tabs.map{it.id})}
        // Simulate a finish already in flight when history was erased.
        ui.runOnIdle{tab.web.webViewClient.onPageFinished(tab.web,tab.url);assertTrue(c.store.history().isEmpty());c.store.visit(tab.url,tab.title)}
        clear(BrowsingTimeRange.ALL_TIME,BrowsingDataSelection(false,true,false))
        ui.runOnIdle{assertEquals(listOf(tab.url),c.store.history().map{it.first});assertFalse(tab in c.tabs)}
    }
    @Test fun actualCookiesStorageHttpCacheAndIconsClearWithoutTouchingPrivateProfile(){
        val server=CacheTestServer()
        try{
            ui.runOnIdle{assertTrue(c.browsingDataCleaner.supportsSiteData);assertTrue(c.privateSession.supported);c.navigate(server.url)}
            val normal=c.active!!
            ui.waitUntil(15000){server.images.get()==1&&normal.progress==100&&normal.title=="快取測試"}
            assertEquals("true",eval(normal,"document.cookie='normalLogin=yes;path=/';localStorage.setItem('normal','yes');true"))
            ui.runOnIdle{normal.web.reload()}
            ui.waitUntil(15000){server.pages.get()>=2&&normal.progress==100&&normal.pendingUrl.isEmpty()}
            assertEquals("The second real load must reuse the HTTP image cache before deletion",1,server.images.get())
            val private=f.load("https://private-deletion.example/","保留無痕",true)
            eval(private,"document.cookie='privateLogin=yes;path=/';localStorage.setItem('private','yes');true")
            val history=c.store.historyPages();val ids=c.tabs.map{it.id}
            val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
            ui.runOnIdle{c.icons.remember("https://icon-deletion.example/",bitmap)}
            val icons=File(ui.activity.cacheDir,"site-icons")
            ui.waitUntil(5000){icons.listFiles().orEmpty().isNotEmpty()}
            clear(BrowsingTimeRange.LAST_15_MINUTES,BrowsingDataSelection(false,false,true))
            ui.runOnIdle{assertEquals(ids,c.tabs.map{it.id});assertEquals(history,c.store.historyPages());assertNull(c.icons.peek("https://icon-deletion.example/"))}
            assertEquals(0,icons.listFiles().orEmpty().size)
            assertEquals("false",eval(normal,"document.cookie.includes('normalLogin')"))
            assertEquals("null",eval(normal,"localStorage.getItem('normal')"))
            assertEquals("true",eval(private,"document.cookie.includes('privateLogin=yes')"))
            assertEquals("\"yes\"",eval(private,"localStorage.getItem('private')"))
            ui.runOnIdle{normal.web.reload()}
            ui.waitUntil(15000){server.images.get()>=2&&normal.progress==100}
            assertEquals("Deleted HTTP cache must be fetched again",2,server.images.get())
        }finally{server.close()}
    }
    @Test fun darkDialogDisablesEmptySelectionAndExplicitlyLabelsAllTimeWebsiteData(){
        ui.runOnIdle{c.store.theme="dark"};ui.activityRule.scenario.recreate();ui.waitForIdle()
        ui.runOnIdle{c.sheet="clear-browsing-data"}
        ui.onNodeWithTag("delete-history").performClick();ui.onNodeWithTag("delete-tabs").performClick()
        ui.onNodeWithTag("confirm-delete-browsing-data").assertIsNotEnabled()
        ui.onNodeWithTag("delete-site-data").performScrollTo().performClick()
        ui.onNodeWithTag("confirm-delete-browsing-data").assertIsEnabled()
        ui.onNodeWithText("不限時間 · 全部清除，可能會登出網站").assertIsDisplayed()
        f.screenshot("delete-browsing-data-dark")
        ui.onNodeWithText("取消").performClick()
        ui.runOnIdle{c.store.theme="system"}
    }
    @Test fun successfulPageRestorationPreservesItsOriginalHistoryAndTabTimes(){
        val server=CacheTestServer()
        try{
            ui.runOnIdle{c.navigate(server.url)}
            ui.waitUntil(15000){server.pages.get()>=1&&c.active?.title=="快取測試"&&c.active?.pendingUrl==""}
            val old=System.currentTimeMillis()-86_400_000
            ui.runOnIdle{c.active!!.lastActiveAt=old;c.store.visit(server.url,"快取測試",old);c.persistTabs()}
            val before=server.pages.get()
            ui.activityRule.scenario.recreate();ui.waitForIdle()
            ui.waitUntil(15000){server.pages.get()>before&&c.active?.pendingUrl==""&&c.active?.progress==100}
            ui.runOnIdle{
                assertEquals(old,c.active!!.lastActiveAt)
                assertEquals(old,c.store.historyPages().first{it.url==server.url}.visitedAt)
            }
            clear(BrowsingTimeRange.LAST_15_MINUTES,BrowsingDataSelection(true,true,false))
            ui.runOnIdle{assertTrue(c.tabs.any{it.url==server.url});assertTrue(c.store.history().any{it.first==server.url})}
        }finally{server.close()}
    }
}

/** A real HTTP cacheable resource, not a mocked clearCache() call. */
private class CacheTestServer:AutoCloseable {
    private val server=ServerSocket(0)
    val pages=AtomicInteger();val images=AtomicInteger()
    val url="http://127.0.0.1:${server.localPort}/page.html"
    private val worker=Thread{
        while(!server.isClosed)runCatching{server.accept().use{socket->
            socket.soTimeout=5000
            val reader=socket.getInputStream().bufferedReader()
            val path=reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
            while(!reader.readLine().isNullOrEmpty())Unit
            val image=path.startsWith("/cached.svg")
            if(image)images.incrementAndGet()else if(path.startsWith("/page.html"))pages.incrementAndGet()
            val body=if(image)"<svg xmlns='http://www.w3.org/2000/svg' width='80' height='80'><rect width='80' height='80' fill='green'/></svg>"
                else "<html><head><title>快取測試</title></head><body><img src='/cached.svg'/></body></html>"
            val bytes=body.toByteArray()
            socket.getOutputStream().use{out->
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: ${if(image)"image/svg+xml"else"text/html"}; charset=utf-8\r\n"+
                    "Cache-Control: ${if(image)"public, max-age=31536000"else"no-store"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(bytes);out.flush()
            }
        }}
    }.apply{isDaemon=true;start()}
    override fun close(){server.close();worker.join(1000)}
}

/** The driver runs each method in a new Android process. */
class BrowsingDataColdRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    private val prefs get()=ui.activity.getSharedPreferences("browsing-data-restart-test",Context.MODE_PRIVATE)
    @Before fun onlyQa(){assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun seedAndDelete(){
        f.reset()
        val kept=f.load("https://browsing-restart.invalid/kept","保留頁面")
        val removed=f.load("https://browsing-restart.invalid/removed","刪除頁面")
        val old=System.currentTimeMillis()-86_400_000
        ui.runOnIdle{
            kept.lastActiveAt=old;c.store.clearHistory();c.store.visit(kept.url,kept.title,old);c.store.visit(removed.url,removed.title)
            c.persistTabs();c.previews.flush()
            prefs.edit().putString("kept",kept.previewKey).putString("removed",removed.previewKey).putLong("old",old).putInt("pid",android.os.Process.myPid()).commit()
            c.browsingDataCleaner.clear(BrowsingTimeRange.LAST_15_MINUTES,BrowsingDataSelection(true,true,false))
        }
        ui.waitUntil(20000){!c.browsingDataCleaner.running}
        ui.runOnIdle{assertEquals("",c.browsingDataCleaner.error);assertEquals(listOf(kept.url),c.store.history().map{it.first});assertEquals(listOf(kept.url),c.store.tabs())}
    }
    @Test fun deletedDataStaysDeletedAndRestorationDoesNotResetAge(){
        assertNotEquals(prefs.getInt("pid",0),android.os.Process.myPid())
        val key=prefs.getString("kept",null)!!;val removed=prefs.getString("removed",null)!!
        ui.runOnIdle{
            assertEquals(listOf("https://browsing-restart.invalid/kept"),c.store.tabs())
            assertEquals(listOf("https://browsing-restart.invalid/kept"),c.store.history().map{it.first})
            assertEquals(prefs.getLong("old",0),c.tabs.single().lastActiveAt)
            assertEquals(prefs.getLong("old",0),c.store.historyPages().single().visitedAt)
            assertTrue(c.tabs.none{it.previewKey==removed});assertEquals(key,c.tabs.single().previewKey)
            assertFalse(f.file(removed).exists())
        }
    }
}
