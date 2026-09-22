package tw.techtarian.browser

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ExternalLinksTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val receiver="tw.techtarian.browser.smoketests"
    private lateinit var server:LinkTestServer
    @Before fun prepare(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        assertTrue("Install the independent receiver fixture before this test",shell("pm path $receiver").contains("package:"))
        PreviewFixture(ui).reset();server=LinkTestServer(receiver)
        // Only the synthetic test domain and test app: no user defaults are changed.
        shell("pm set-app-links --package $receiver 2 links.chengjing.test")
        shell("pm set-app-links-allowed --user 0 --package $receiver true")
        shell("pm set-app-links-user-selection --user 0 --package $receiver true links.chengjing.test")
    }
    @After fun finish(){
        if(device.currentPackageName==receiver)device.pressBack()
        ui.runOnIdle{c.closePrivateTabs();c.sheet=""}
        if(::server.isInitialized)server.close()
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun load(private:Boolean=false):BrowserTab {
        ui.runOnIdle{c.newTab(server.url,private)}
        ui.waitUntil(15000){c.active?.title=="Deep link fixture"&&c.active?.pendingUrl==""}
        ui.waitUntil(10000){c.active?.web?.isAttachedToWindow==true&&eval("document.links.length")=="7"}
        return c.active!!
    }
    private fun eval(code:String):String{
        val done=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{c.active!!.web.evaluateJavascript(code){result.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    private fun click(label:String){
        if(!label.startsWith("Open ")){
            ui.waitUntil(10000){ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()}
            val node=device.wait(Until.findObject(By.text(label)),10000);assertNotNull(label,node);node!!.click();return
        }
        // Read the fixture's actual DOM, then deliver a real touch. WebView's virtual
        // accessibility tree can refer to the previously detached WebView in a test run.
        val find="Array.from(document.links).find(a=>a.textContent===${JSONObject.quote(label)})"
        eval("(()=>{const a=$find;a.scrollIntoView({block:'center'});return true;})()")
        ui.waitForIdle()
        val position=JSONObject(eval("(()=>{const r=($find).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio};})()"))
        val origin=IntArray(2);ui.runOnUiThread{c.active!!.web.getLocationOnScreen(origin)}
        assertTrue(device.click(origin[0]+(position.getDouble("x")*position.getDouble("dpr")).toInt(),origin[1]+(position.getDouble("y")*position.getDouble("dpr")).toInt()))
    }
    private fun received(url:String){
        assertTrue("The other app must actually become visible",device.wait(Until.hasObject(By.pkg(receiver)),15000))
        assertNotNull(device.wait(Until.findObject(By.desc("deep-link-received:$url")),10000))
        shell("mkdir -p /data/local/tmp/chengjing-ui")
        shell("screencap -p /data/local/tmp/chengjing-ui/deep-link-receiver.png")
    }
    private fun back(){
        device.pressBack()
        ui.waitUntil(10000){ui.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}
        ui.waitForIdle()
        assertNotNull(device.wait(Until.findObject(By.desc("瀏覽器選單")),10000))
    }

    @Test fun customSchemeReallyOpensAnotherAppAndBackKeepsThePage(){
        val tab=load();val before=c.tabs.map{it.id}
        click("Open custom app");received("chengjing-test://open/watch?id=123")
        back()
        ui.runOnIdle{assertEquals(before,c.tabs.map{it.id});assertEquals(tab.id,c.activeId);assertEquals(server.url,tab.url);assertFalse(c.store.tabs().any{it.startsWith("chengjing-test:")})}
    }
    @Test fun intentAndClickedServerRedirectBothReachTheReceiver(){
        val tab=load();click("Open intent app");received("chengjing-test://open/watch?id=456");back()
        click("Open redirected app");received("chengjing-test://open/watch?id=redirect");back()
        ui.runOnIdle{assertEquals(server.url,tab.url);assertEquals(100,tab.progress)}
    }
    @Test fun clickedNewWindowHandsOffWithoutLeavingAnEmptyTab(){
        val tab=load();val before=c.tabs.map{it.id}
        click("Open new window app");received("chengjing-test://open/watch?id=popup");back()
        ui.runOnIdle{assertEquals(before,c.tabs.map{it.id});assertEquals(tab.id,c.activeId)}
    }
    @Test fun missingAppLoadsWebFallbackInTheSameTab(){
        val tab=load();val count=c.tabs.size
        click("Open missing app")
        ui.waitUntil(15000){tab.title=="Web fallback"&&tab.pendingUrl.isEmpty()}
        ui.runOnIdle{assertEquals(count,c.tabs.size);assertEquals(tab.id,c.activeId);assertEquals(server.fallback,tab.url)}
        assertEquals(ui.activity.packageName,device.currentPackageName)
    }
    @Test fun verifiedWebLinkOpensItsAppAndOrdinaryWebLinkStaysHere(){
        val tab=load()
        ui.runOnIdle{
            val intent=ExternalLinks.parse("https://links.chengjing.test/video/789")!!.intent
            val pm=ui.activity.packageManager
            @Suppress("DEPRECATION")
            val resolved=pm.resolveActivity(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("Default handler: ${resolved?.activityInfo?.packageName}/${resolved?.activityInfo?.name}; exported=${resolved?.activityInfo?.exported}",ExternalLinks.hasDefaultWebApp(ui.activity,intent))
        }
        click("Open verified app");received("https://links.chengjing.test/video/789");back()
        click("Open ordinary web")
        ui.waitUntil(15000){tab.title=="Web fallback"&&tab.pendingUrl.isEmpty()}
        assertEquals(ui.activity.packageName,device.currentPackageName)
    }
    @Test fun javascriptWithoutGestureAndFramesCannotLaunchApps(){
        val tab=load()
        ui.runOnIdle{tab.web.evaluateJavascript("setTimeout(()=>location.href='chengjing-test://open/watch?id=auto',0)",null)}
        ui.waitUntil(10000){tab.blockedEvents.any{it.url.contains("id=auto")}}
        ui.runOnIdle{
            assertTrue(tab.web.webViewClient.shouldOverrideUrlLoading(tab.web,request("chengjing-test://open/watch?id=frame",false,true,false)))
            assertEquals(server.url,tab.url)
        }
        assertEquals(ui.activity.packageName,device.currentPackageName)
    }
    @Test fun disablingAndroidLinkHandlingKeepsTheWebNavigationInTheBrowser(){
        val tab=load()
        shell("pm set-app-links-allowed --user 0 --package $receiver false")
        ui.runOnIdle{assertFalse(ExternalLinks.hasDefaultWebApp(ui.activity,ExternalLinks.parse("https://links.chengjing.test/video/789")!!.intent))}
        click("Open verified app")
        ui.waitUntil(15000){tab.url=="https://links.chengjing.test/video/789"}
        assertEquals(ui.activity.packageName,device.currentPackageName)
    }
    @Test fun typedAddressRedirectHasNoExternalLaunchAuthorization(){
        val tab=load()
        ui.runOnIdle{c.navigate(server.redirect)}
        ui.waitUntil(10000){tab.blockedEvents.any{it.url.contains("id=redirect")}}
        assertEquals(ui.activity.packageName,device.currentPackageName)
    }
    @Test fun privateModeRequiresConfirmationAndDoesNotChangeCollection(){
        ui.runOnIdle{assertTrue(c.privateSession.supported)}
        val tab=load(true)
        click("Open custom app")
        ui.waitUntil(10000){ui.onAllNodesWithText("在其他 App 開啟？").fetchSemanticsNodes().isNotEmpty()}
        assertNotNull(device.wait(Until.findObject(By.text("在其他 App 開啟？")),10000))
        click("取消")
        assertEquals(ui.activity.packageName,device.currentPackageName)
        val before=c.tabs.map{it.id}
        click("Open new window app");click("取消")
        ui.runOnIdle{assertEquals(before,c.tabs.map{it.id});assertEquals(tab.id,c.activeId)}
        click("Open custom app");click("開啟");received("chengjing-test://open/watch?id=123");back()
        ui.runOnIdle{assertEquals(tab.id,c.activeId);assertTrue(c.active!!.incognito);assertFalse(c.store.tabs().contains(tab.url))}
    }
    @Test fun intentSanitizationDropsPrivilegedFieldsAndUnsafeFallbacks(){
        val raw="intent://open/watch#Intent;scheme=chengjing-test;package=$receiver;action=android.intent.action.DELETE;component=$receiver/.PrivateActivity;launchFlags=0x10000003;S.injected=secret;S.browser_fallback_url=javascript%3Aalert(1);end"
        val link=ExternalLinks.parse(raw)!!
        assertEquals(Intent.ACTION_VIEW,link.intent.action);assertEquals(setOf(Intent.CATEGORY_BROWSABLE),link.intent.categories)
        assertNull(link.intent.component);assertNull(link.intent.selector);assertNull(link.intent.extras);assertNull(link.intent.clipData);assertEquals(0,link.intent.flags)
        assertEquals(receiver,link.intent.`package`);assertNull(link.fallback)
        listOf("file:///data/private","content://private/data","javascript:alert(1)","intent://secret#Intent;scheme=content;end","intent://broken#Intent;").forEach{assertNull(it,ExternalLinks.parse(it))}
    }
    private fun request(url:String,main:Boolean,gesture:Boolean,redirect:Boolean)=object:WebResourceRequest{
        override fun getUrl()=Uri.parse(url)
        override fun isForMainFrame()=main
        override fun isRedirect()=redirect
        override fun hasGesture()=gesture
        override fun getMethod()="GET"
        override fun getRequestHeaders()=emptyMap<String,String>()
    }
}

private class LinkTestServer(receiver:String):AutoCloseable {
    private val server=ServerSocket(0)
    val url="http://127.0.0.1:${server.localPort}/links"
    val fallback="http://127.0.0.1:${server.localPort}/fallback"
    val redirect="http://127.0.0.1:${server.localPort}/redirect"
    private val worker=Thread{
        while(!server.isClosed)runCatching{server.accept().use{socket->
            socket.soTimeout=5000
            val reader=socket.getInputStream().bufferedReader();val path=reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
            while(!reader.readLine().isNullOrEmpty()){}
            if(path=="/redirect"){
                socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: chengjing-test://open/watch?id=redirect\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }else{
                val content=if(path=="/fallback")"<title>Web fallback</title><h1>Web fallback</h1>"else """
                    <title>Deep link fixture</title><meta name='viewport' content='width=device-width,initial-scale=1'>
                    <style>a{display:block;padding:12px;font:16px sans-serif}</style>
                    <a href='chengjing-test://open/watch?id=123'>Open custom app</a>
                    <a href='intent://open/watch?id=456#Intent;scheme=chengjing-test;package=$receiver;end'>Open intent app</a>
                    <a href='$redirect'>Open redirected app</a>
                    <a href='chengjing-test://open/watch?id=popup' target='_blank'>Open new window app</a>
                    <a href='intent://open/watch#Intent;scheme=chengjing-missing;package=invalid.missing.app;S.browser_fallback_url=${URLEncoder.encode(fallback,"UTF-8")};end'>Open missing app</a>
                    <a href='https://links.chengjing.test/video/789'>Open verified app</a>
                    <a href='$fallback'>Open ordinary web</a>
                """.trimIndent()
                val bytes="<html><head>$content</head><body></body></html>".toByteArray()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                socket.getOutputStream().write(bytes)
            }
            socket.getOutputStream().flush()
        }}
    }.apply{isDaemon=true;start()}
    override fun close(){server.close();worker.join(1000)}
}
