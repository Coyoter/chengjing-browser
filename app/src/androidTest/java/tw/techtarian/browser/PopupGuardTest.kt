package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real touch events and Chromium window creation, including POST and opener preservation. */
class PopupGuardTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private lateinit var server:MockWebServer
    private val requests=ConcurrentLinkedQueue<RecordedRequest>()
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));PreviewFixture(ui).reset()
        val video=InstrumentationRegistry.getInstrumentation().context.assets.open("download-video.b64").bufferedReader().use{android.util.Base64.decode(it.readText(),android.util.Base64.DEFAULT)}
        server=MockWebServer().apply{dispatcher=object:Dispatcher(){override fun dispatch(r:RecordedRequest):MockResponse{
            requests.add(r)
            if(r.path=="/video")return MockResponse().setHeader("Content-Type","video/mp4").setBody(okio.Buffer().write(video))
            if(r.path=="/redirect")return MockResponse().setResponseCode(302).setHeader("Location","/redirected")
            return MockResponse().setHeader("Content-Type","text/html; charset=utf-8").setHeader("Cache-Control","no-store").setBody("""
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>Popup QA ${r.path}</title>
                <style>a,button{display:block;width:fit-content;margin:20px;padding:15px}img{width:60px;height:40px}</style>
                <button id="play" onclick="document.getElementById('video').play();window.playing=(window.playing||0)+1;window.open('/ad')">Play</button>
                <video id="video" src="/video" muted loop playsinline width="100" height="60"></video>
                <a id="link" href="/article" target="_blank">Article</a>
                <a id="image" href="/image-article" target="_blank"><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="></a>
                <a id="hijacked" href="/wanted" target="_blank" onclick="window.open('/unwanted')">Wanted link with an extra ad</a>
                <form id="checkout" action="/checkout" method="post" target="checkout"><input name="token" value="original-post"></form>
                <button id="submit" onclick="document.getElementById('checkout').submit()">Checkout</button>
                <a id="same" href="/same">Same tab</a>
                <a id="redirect" href="/redirect" target="_blank">Redirecting article</a>
                <a id="script" href="javascript:void(0)" onclick="document.getElementById('checkout').submit()">Script checkout link</a>
            """.trimIndent())
        }};start()}
    }
    @After fun cleanup(){ui.runOnIdle{c.sheet="";c.prompts.cancel();c.closePrivateTabs()};server.shutdown()}
    private fun eval(tab:BrowserTab,code:String):String{
        var result="";val done=CountDownLatch(1)
        ui.runOnUiThread{tab.web.evaluateJavascript(code){result=it;done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result
    }
    private fun load(guard:Boolean=true,private:Boolean=false):BrowserTab{
        val url=server.url("/main").toString()
        var tab:BrowserTab?=null
        ui.runOnIdle{tab=c.newTab(url,private)}
        ui.waitUntil(10000){tab!!.title=="Popup QA /main"&&tab!!.pendingUrl.isEmpty()}
        ui.runOnIdle{c.saveSite(c.store.get(Domains.scope(tab!!.url)).copy(guard=guard))}
        return tab!!
    }
    private fun tap(tab:BrowserTab,id:String){
        ui.waitForIdle()
        eval(tab,"document.getElementById('$id').scrollIntoView({block:'center'});true")
        val painted=CountDownLatch(1)
        ui.runOnUiThread{tab.web.postVisualStateCallback(1,object:android.webkit.WebView.VisualStateCallback(){override fun onComplete(id:Long){painted.countDown()}})}
        assertTrue(painted.await(10,TimeUnit.SECONDS));ui.waitForIdle()
        val p=JSONObject(eval(tab,"(()=>{const e=document.getElementById('$id');const r=(e.querySelector('img')||e).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,d:devicePixelRatio}})()"))
        val origin=IntArray(2);ui.runOnUiThread{tab.web.getLocationOnScreen(origin)}
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(origin[0]+(p.getDouble("x")*p.getDouble("d")).toInt(),origin[1]+(p.getDouble("y")*p.getDouble("d")).toInt())
        ui.runOnIdle{println("Popup tap $id: hit=${tab.web.hitTestResult.type}/${tab.web.hitTestResult.extra}, tabs=${c.tabs.map{it.url}}, blocked=${tab.blockedEvents.map{it.url+" pending="+(it.openWindow!=null)}}")}
    }
    @Test fun playContinuesWhileAdWaitsForExplicitManualOpening(){
        val tab=load();val count=c.tabs.size
        tap(tab,"play")
        ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        assertEquals("1",eval(tab,"window.playing"));assertEquals(tab.id,c.activeId);assertEquals(count,c.tabs.size)
        ui.waitUntil(10000){eval(tab,"(()=>{const v=document.getElementById('video');return !v.paused&&v.currentTime>0.1})()") == "true"}
        assertFalse(requests.any{it.path=="/ad"})
        ui.runOnIdle{c.sheet="blocked"}
        ui.onNodeWithText(bt(R.string.msg_b7d4526756b3)).performClick()
        ui.waitUntil(10000){c.active?.title=="Popup QA /ad"}
        assertEquals("",c.sheet)
        assertEquals(count+1,c.tabs.size)
        assertEquals("true",eval(c.active!!,"!!window.opener"))
    }
    @Test fun actualTextAndImageLinksAndSameTabLinksStillWork(){
        val tab=load();val count=c.tabs.size
        tap(tab,"link");ui.waitUntil(10000){c.active?.title=="Popup QA /article"}
        assertEquals(count+1,c.tabs.size)
        ui.runOnIdle{c.switchTab(tab.id)}
        tap(tab,"image");ui.waitUntil(10000){c.active?.title=="Popup QA /image-article"}
        assertEquals(count+2,c.tabs.size)
        ui.runOnIdle{c.switchTab(tab.id)}
        tap(tab,"same");ui.waitUntil(10000){tab.title=="Popup QA /same"}
        assertEquals(tab.id,c.activeId);assertEquals(count+2,c.tabs.size)
    }
    @Test fun linkClickCannotUseItsGestureToOpenADifferentAdDestination(){
        val tab=load();val count=c.tabs.size
        tap(tab,"hijacked")
        ui.waitUntil(10000){c.active?.title=="Popup QA /wanted"}
        assertEquals(count+1,c.tabs.size)
        assertFalse(requests.any{it.path=="/unwanted"})
        assertTrue(tab.blockedTotal>0)
    }
    @Test fun manuallyAllowedWindowKeepsOriginalPostBody(){
        val tab=load();tap(tab,"submit")
        ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        assertFalse(requests.any{it.path=="/checkout"})
        ui.runOnIdle{c.sheet="blocked"}
        ui.onNodeWithText(bt(R.string.msg_b7d4526756b3)).performClick()
        ui.waitUntil(10000){c.active?.title=="Popup QA /checkout"}
        val request=requests.first{it.path=="/checkout"}
        assertEquals("POST",request.method);assertEquals("token=original-post",request.body.readUtf8())
    }
    @Test fun guardOffAllowsClickedScriptWindowButNotUnsolicitedWindows(){
        val tab=load(guard=false);val count=c.tabs.size
        eval(tab,"window.open('/unsolicited');true");ui.waitForIdle()
        assertEquals(count,c.tabs.size)
        tap(tab,"play");ui.waitUntil(10000){c.active?.title=="Popup QA /ad"}
        assertEquals(count+1,c.tabs.size)
    }
    @Test fun redirectsAndScriptLinksHaveWorkingOpeningPaths(){
        val tab=load();tap(tab,"redirect")
        ui.waitUntil(10000){c.active?.title=="Popup QA /redirected"}
        ui.runOnIdle{c.switchTab(tab.id)}
        tap(tab,"script");ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        ui.runOnIdle{tab.blockedEvents.first{it.openWindow!=null}.openWindow!!.invoke()}
        ui.waitUntil(10000){c.active?.title=="Popup QA /checkout"}
        assertEquals("token=original-post",requests.first{it.path=="/checkout"}.body.readUtf8())
    }
    @Test fun pendingAdCannotBreakTheNextNormalLink(){
        val tab=load();tap(tab,"play")
        ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        tap(tab,"link");ui.waitUntil(10000){c.active?.title=="Popup QA /article"}
        assertFalse(requests.any{it.path=="/ad"})
        assertTrue(tab.blockedEvents.all{it.openWindow==null})
    }
    @Test fun privateWindowsStayPrivateAndNavigationInvalidatesBlockedRequests(){
        val tab=load(private=true);tap(tab,"play")
        ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        ui.runOnIdle{tab.blockedEvents.first{it.openWindow!=null}.openWindow!!.invoke()}
        ui.waitUntil(10000){c.active?.title=="Popup QA /ad"}
        assertTrue(c.active!!.incognito)
        ui.runOnIdle{c.switchTab(tab.id)}
        tap(tab,"play");ui.waitUntil(10000){tab.blockedEvents.any{it.openWindow!=null}}
        tap(tab,"same");ui.waitUntil(10000){tab.title=="Popup QA /same"}
        assertTrue(tab.blockedEvents.all{it.openWindow==null})
    }
}
