package tw.techtarian.browser

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.net.ServerSocket

class BackNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private lateinit var server:BackTestServer
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        PreviewFixture(ui).reset();server=BackTestServer()
        ui.runOnIdle{c.home.updateEnabled(true);c.home.useDefault()}
    }
    @After fun cleanup(){ui.runOnIdle{c.home.updateEnabled(false);c.home.useDefault();c.sheet=""};if(::server.isInitialized)server.close()}
    private fun waitFor(path:String){ui.waitUntil(10000){c.active?.url==server.url(path)&&c.active?.pendingUrl==""&&c.active?.title=="Back fixture $path"}}
    private fun regular(path:String="a"){ui.runOnIdle{c.navigate(server.url(path))};waitFor(path)}
    private fun deliver(path:String){
        val activity=ui.activity;val launch=activity.intent
        try{
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,Intent(Intent.ACTION_VIEW,Uri.parse(server.url(path))))
            assertNull(activity.intent.data)
        }finally{
            // ActivityScenario identifies lifecycle events by its original launch Intent.
            activity.intent=launch
        }
    }
    private fun incoming(path:String="external"){
        ui.runOnIdle{deliver(path)};waitFor(path)
    }
    @Test fun regularRootBackKeepsTheWebpageAndDoesNotCreateHome(){
        regular();val key=c.active!!.previewKey
        ui.runOnIdle{assertFalse(c.goBackInPage());c.finishBackNavigation();assertEquals(listOf(server.url("a")),c.store.tabs());assertEquals(key,c.active!!.previewKey)}
    }
    @Test fun repeatedExternalLinksCloseWithoutAccumulatingEmptyTabs(){
        regular();val resident=c.active!!.previewKey
        repeat(4){
            incoming();ui.runOnIdle{
                assertEquals(2,c.tabs.size);assertTrue(c.active!!.openedExternally);assertFalse(c.goBackInPage())
                c.finishBackNavigation();assertEquals(listOf(server.url("a")),c.store.tabs());assertEquals(resident,c.active!!.previewKey)
            }
        }
    }
    @Test fun anExternalLinkCanReuseUnusedHomeAndLeaveNoSavedPlaceholder(){
        val homeKey=c.active!!.previewKey
        incoming();ui.runOnIdle{
            assertEquals(1,c.tabs.size);assertEquals(homeKey,c.active!!.previewKey)
            c.finishBackNavigation();assertTrue(c.tabs.isEmpty());assertTrue(c.store.tabs().isEmpty())
        }
        ui.activityRule.scenario.recreate()
        ui.runOnIdle{assertEquals(1,c.tabs.size);assertEquals("",c.active!!.url);assertFalse(c.active!!.openedExternally)}
    }
    @Test fun incomingIntentIsNotReplayedAfterRecreationAndItsOriginSurvives(){
        incoming();val key=c.active!!.previewKey
        ui.runOnIdle{assertNull(ui.activity.intent.data);assertTrue(c.store.savedTabRecords().single().openedExternally)}
        ui.activityRule.scenario.recreate();waitFor("external")
        ui.runOnIdle{assertEquals(1,c.tabs.size);assertEquals(key,c.active!!.previewKey);assertTrue(c.active!!.openedExternally);c.finishBackNavigation();assertTrue(c.store.tabs().isEmpty())}
    }
    @Test fun backTraversesRealPagesAndSkipsTheNativeHomeEntry(){
        regular("a")
        ui.onNodeWithTag("home-button").performClick();ui.waitUntil(10000){c.active?.url==""&&c.active?.pendingUrl==""}
        regular("b")
        ui.onNodeWithContentDescription("上一頁").assertIsEnabled().performClick();waitFor("a")
        ui.onNodeWithContentDescription("上一頁").assertIsNotEnabled()
        ui.runOnIdle{assertFalse(c.goBackInPage());assertEquals(1,c.tabs.size)}
    }
    @Test fun aHomeWithRealHistoryIsNotOverwrittenByIncomingLinks(){
        regular("a");val original=c.active!!.previewKey
        ui.onNodeWithTag("home-button").performClick();ui.waitUntil(10000){c.active?.url==""&&c.active?.pendingUrl==""}
        incoming();ui.runOnIdle{assertEquals(2,c.tabs.size);assertNotEquals(original,c.active!!.previewKey);c.finishBackNavigation();assertEquals(original,c.active!!.previewKey);assertTrue(c.goBackInPage())};waitFor("a")
    }
    @Test fun legacySavedTabsRemainOrdinaryAndAnUnloadedExternalTabCanBeClosed(){
        ui.runOnIdle{
            c.store.saveTabs(listOf(server.url("legacy")))
            assertFalse(c.store.savedTabRecords().single().openedExternally)
            deliver("pending")
            c.finishBackNavigation();assertTrue(c.tabs.isEmpty());assertTrue(c.store.tabs().isEmpty())
        }
    }
}

class ColdExternalBackNavigationTest {
    @Test fun rebuildingAnActivityLaunchedByAnExternalIntentDoesNotDuplicateTheTab(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(context.packageName.endsWith(".qa"))
        BrowserStore(context).saveTabs(emptyList())
        BackTestServer().use{server->
            val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(server.url("cold")))
            androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use{scenario->
                fun waitForPage(){
                    val end=System.currentTimeMillis()+15000;var loaded=false
                    while(!loaded&&System.currentTimeMillis()<end){scenario.onActivity{loaded=it.controller.active?.title=="Back fixture cold"&&it.controller.active?.pendingUrl==""};if(!loaded)Thread.sleep(100)}
                    assertTrue("External page must finish loading",loaded)
                }
                waitForPage();var key:String?=null
                scenario.onActivity{
                    assertNull(it.intent.data);assertEquals(1,it.controller.tabs.size);key=it.controller.active!!.previewKey;assertTrue(it.controller.active!!.openedExternally)
                    // Restore Scenario's bookkeeping; also exercise recreation with the original VIEW URL still present.
                    it.intent=intent
                }
                scenario.recreate();waitForPage()
                scenario.onActivity{assertEquals(1,it.controller.tabs.size);assertEquals(key,it.controller.active!!.previewKey);assertTrue(it.controller.active!!.openedExternally)}
            }
        }
    }
}

private class BackTestServer:AutoCloseable {
    private val server=ServerSocket(0)
    fun url(path:String)="http://127.0.0.1:${server.localPort}/$path"
    private val worker=Thread{
        while(!server.isClosed)runCatching{server.accept().use{socket->
            socket.soTimeout=3000;val reader=socket.getInputStream().bufferedReader()
            val path=reader.readLine()?.split(' ')?.getOrNull(1)?.removePrefix("/").orEmpty()
            while(!reader.readLine().isNullOrEmpty()){}
            val bytes="<html><head><title>Back fixture $path</title><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><h1>Back fixture $path</h1><a href='/b'>Next article</a></body></html>".toByteArray()
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());socket.getOutputStream().write(bytes);socket.getOutputStream().flush()
        }}
    }.apply{isDaemon=true;start()}
    override fun close(){server.close();worker.join(1000)}
}
