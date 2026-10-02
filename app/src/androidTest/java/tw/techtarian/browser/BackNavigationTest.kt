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
    private fun back(){
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use{it.readBytes()}
        ui.waitForIdle()
        ui.runOnIdle{assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,ui.activity.lifecycle.currentState)}
    }
    private fun leaveInitialAndReopen(private:Boolean=false){
        val activity=ui.activity
        val initialId=c.active!!.id
        val count=c.tabs.size
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use{it.readBytes()}
        shell("input keyevent KEYCODE_BACK")
        ui.waitUntil(10000){!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)&&androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).currentPackageName!=activity.packageName}
        assertNotEquals("Initial-page Back must leave the browser",activity.packageName,androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).currentPackageName)
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${activity.packageName}/tw.techtarian.browser.MainActivity")
        ui.waitUntil(10000){activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.RESUMED}
        initial(private)
        ui.runOnIdle{assertEquals(initialId,c.active!!.id);assertEquals(count,c.tabs.size)}
    }
    private fun initial(private:Boolean=false){
        ui.waitUntil(10000){c.active?.url==""&&c.active?.pendingUrl==""&&c.active?.incognito==private}
        ui.runOnIdle{assertTrue(c.active!!.isInitialNewTab());assertFalse(c.active!!.openedExternally);assertFalse(c.active!!.canBack)}
        ui.onNodeWithTag(if(private)"incognito-indicator" else "browser-home").assertIsDisplayed()
        ui.onNodeWithContentDescription("上一頁").assertIsNotEnabled()
    }
    @Test fun systemBackTraversesHistoryClosesThePageThenLeavesFromInitial(){
        regular("a");val closed=c.active!!.id;val key=c.active!!.previewKey
        regular("b");back();waitFor("a")
        back();initial()
        ui.runOnIdle{assertEquals(listOf(""),c.store.tabs());assertEquals(1,c.tabs.size);assertNotEquals(closed,c.active!!.id);assertNotEquals(key,c.active!!.previewKey)}
        PreviewFixture(ui).screenshot("root-back-new-tab")
        val initialId=c.active!!.id
        repeat(3){leaveInitialAndReopen();ui.runOnIdle{assertEquals(initialId,c.active!!.id);assertEquals(1,c.tabs.size)}}
    }
    @Test fun repeatedExternalLinksCloseToOneInitialTabAndPreserveOtherPages(){
        regular();val resident=c.active!!.id
        repeat(4){
            incoming();val external=c.active!!.id
            ui.runOnIdle{assertEquals(2,c.tabs.size);assertTrue(c.active!!.openedExternally);assertFalse(c.goBackInPage())}
            back();initial()
            ui.runOnIdle{assertEquals(2,c.tabs.size);assertEquals(listOf(server.url("a"),""),c.store.tabs());assertTrue(c.tabs.any{it.id==resident});assertTrue(c.tabs.none{it.id==external})}
        }
    }
    @Test fun closingARootPageReusesAnExistingInitialTab(){
        regular("a");val resident=c.active!!.id
        var initialId=0
        ui.runOnIdle{initialId=c.newTab()!!.id;c.newTab(server.url("b"))};waitFor("b")
        val closed=c.active!!.id
        ui.onNodeWithContentDescription("上一頁").assertIsEnabled().performClick();initial()
        ui.runOnIdle{assertEquals(initialId,c.active!!.id);assertEquals(2,c.tabs.size);assertTrue(c.tabs.any{it.id==resident});assertTrue(c.tabs.none{it.id==closed})}
    }
    @Test fun anExternalLinkCanReuseUnusedHomeAndReturnToOneSavedInitialTab(){
        val homeKey=c.active!!.previewKey
        incoming();val closed=c.active!!.id
        ui.runOnIdle{assertEquals(1,c.tabs.size);assertEquals(homeKey,c.active!!.previewKey)}
        back();initial()
        ui.runOnIdle{assertEquals(listOf(""),c.store.tabs());assertEquals(1,c.tabs.size);assertNotEquals(closed,c.active!!.id)}
        val initialKey=c.active!!.previewKey
        ui.activityRule.scenario.recreate();initial()
        ui.runOnIdle{assertEquals(1,c.tabs.size);assertEquals(initialKey,c.active!!.previewKey)}
    }
    @Test fun incomingIntentIsNotReplayedAfterRecreationAndClosedSourceMarkerIsRemoved(){
        incoming();val key=c.active!!.previewKey
        ui.runOnIdle{assertNull(ui.activity.intent.data);assertTrue(c.store.savedTabRecords().single().openedExternally)}
        ui.activityRule.scenario.recreate();waitFor("external")
        ui.runOnIdle{assertEquals(1,c.tabs.size);assertEquals(key,c.active!!.previewKey);assertTrue(c.active!!.openedExternally)}
        back();initial()
        ui.runOnIdle{assertEquals(listOf(""),c.store.tabs());assertFalse(c.store.savedTabRecords().single().openedExternally)}
    }
    @Test fun backTraversesRealPagesAndSkipsTheNativeHomeEntry(){
        regular("a")
        ui.onNodeWithTag("home-button").performClick();ui.waitUntil(10000){c.active?.url==""&&c.active?.pendingUrl==""}
        regular("b")
        ui.onNodeWithContentDescription("上一頁").assertIsEnabled().performClick();waitFor("a")
        ui.onNodeWithContentDescription("上一頁").assertIsEnabled().performClick();initial()
        ui.runOnIdle{assertEquals(1,c.tabs.size)}
    }
    @Test fun aHomeWithRealHistoryIsPreservedButIsNotReusedAsTheInitialTab(){
        regular("a");val original=c.active!!.id
        ui.onNodeWithTag("home-button").performClick();ui.waitUntil(10000){c.active?.url==""&&c.active?.pendingUrl==""}
        incoming();ui.runOnIdle{assertEquals(2,c.tabs.size);assertNotEquals(original,c.active!!.id)}
        back();initial()
        ui.runOnIdle{assertNotEquals(original,c.active!!.id);assertEquals(2,c.tabs.size);c.switchTab(original);assertTrue(c.goBackInPage())};waitFor("a")
    }
    @Test fun privateRootBackShowsAPrivateInitialTabAndKeepsRegularPages(){
        Assume.assumeTrue(c.privateSession.supported)
        regular("a");val resident=c.active!!.id
        ui.runOnIdle{c.newTab(server.url("private"),incognito=true)};waitFor("private")
        val closed=c.active!!.id
        back();initial(private=true)
        ui.runOnIdle{assertEquals(2,c.tabs.size);assertTrue(c.tabs.any{it.id==resident&&!it.incognito});assertTrue(c.tabs.none{it.id==closed});assertEquals(listOf(server.url("a")),c.store.tabs())}
        val initialId=c.active!!.id
        leaveInitialAndReopen(private=true);ui.runOnIdle{assertEquals(initialId,c.active!!.id);assertEquals(2,c.tabs.size)}
    }
    @Test fun legacySavedTabsRemainOrdinaryAndAnUnloadedExternalTabCanBeClosed(){
        ui.runOnIdle{
            c.store.saveTabs(listOf(server.url("legacy")))
            assertFalse(c.store.savedTabRecords().single().openedExternally)
            deliver("pending")
            c.finishBackNavigation();assertEquals(1,c.tabs.size);assertEquals(listOf(""),c.store.tabs())
        }
        initial()
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
