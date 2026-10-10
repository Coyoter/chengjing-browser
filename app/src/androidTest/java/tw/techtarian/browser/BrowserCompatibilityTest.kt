package tw.techtarian.browser

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BrowserCompatibilityTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var server:MockWebServer
    private val visits=ConcurrentLinkedQueue<String>()
    private val uploadedBytes=java.util.concurrent.atomic.AtomicLong(0)
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));PreviewFixture(ui).reset()
        server=MockWebServer().apply{dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            val path=request.path.orEmpty();visits.add(path)
            if(path=="/upload")uploadedBytes.set(request.bodySize)
            return MockResponse().setHeader("Content-Type","text/html; charset=utf-8").setBody("""<title>Browser QA $path</title><meta name="viewport" content="width=device-width,initial-scale=1"><a id="popup" target="_blank" href="/popup">Open popup QA</a>
                <p><input id="photo" type="file" accept="image/*" capture="environment" onchange="const f=this.files[0];window.photoResult={name:f.name,size:f.size,type:f.type};const data=new FormData();data.append('photo',f);fetch('/upload',{method:'POST',body:data}).then(r=>window.uploaded=r.ok)"></p>""")
        }};start()}
    }
    @After fun cleanup(){ui.runOnIdle{c.sheet="";c.prompts.cancel();c.closePrivateTabs()};server.shutdown()}
    private fun eval(tab:BrowserTab,code:String):String{
        var value="";val done=CountDownLatch(1)
        ui.runOnUiThread{tab.web.evaluateJavascript(code){value=it;done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return value
    }
    private fun load():BrowserTab{
        val url=server.url("/main").toString();var tab:BrowserTab?=null
        ui.runOnIdle{tab=c.newTab(url,false)}
        ui.waitUntil(10000){tab!!.title=="Browser QA /main"&&tab!!.pendingUrl.isEmpty()};return tab!!
    }
    @Test fun moreThanTwentyTabsSurviveRestartAndOnlyTheVisiblePageReloads(){
        val urls=(1..25).map{server.url("/page-$it").toString()}
        ui.runOnIdle{
            c.tabs.toList().forEach{c.closeTab(it.id,replaceLast=false)}
            urls.forEach{assertNotNull(c.newTab(it,false))}
        }
        ui.waitUntil(50000){c.tabs.size==25&&c.tabs.all{it.pendingUrl.isEmpty()&&it.title.startsWith("Browser QA /page-")}}
        ui.runOnIdle{c.checkpointTabs();assertEquals(25,c.store.tabs().size)}
        visits.clear()
        ui.activityRule.scenario.recreate()
        ui.waitUntil(20000){c.tabs.size==25&&c.active?.title=="Browser QA /page-25"}
        assertEquals(25,c.store.tabs().size)
        assertEquals(listOf("/page-25"),visits.filter{it.startsWith("/page-")})
        assertEquals(24,c.tabs.count{it.lazyRestore})
        ui.runOnIdle{c.switchTab(c.tabs.first().id)}
        ui.waitUntil(10000){c.active?.title=="Browser QA /page-1"}
        assertEquals(setOf("/page-25","/page-1"),visits.filter{it.startsWith("/page-")}.toSet())
        ui.runOnIdle{assertNotNull(c.newTab())}
        assertEquals(26,c.tabs.size)
    }
    @Test fun userClickedNewWindowWorksWithGuardWhileUnsolicitedPopupsStayBlocked(){
        val tab=load()
        ui.runOnIdle{c.store.save(c.store.get(Domains.scope(tab.url)).copy(guard=true))}
        val count=c.tabs.size
        eval(tab,"window.open('/unsolicited');true")
        ui.waitForIdle()
        assertEquals(count,c.tabs.size)
        val position=JSONObject(eval(tab,"(()=>{const r=document.getElementById('popup').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val origin=IntArray(2);ui.runOnUiThread{tab.web.getLocationOnScreen(origin)}
        device.click(origin[0]+(position.getDouble("x")*position.getDouble("dpr")).toInt(),origin[1]+(position.getDouble("y")*position.getDouble("dpr")).toInt())
        ui.waitUntil(10000){c.active?.title=="Browser QA /popup"}
        assertEquals(count+1,c.tabs.size)
    }
    @Test fun thirdPartyCookieExceptionIsPerSiteAndPrivateChoicesAreTemporary(){
        val tab=load();val permissions=ui.activity.websitePermissions
        ui.runOnIdle{assertFalse(c.cookiesFor(tab).acceptThirdPartyCookies(tab.web))}
        ui.runOnIdle{c.sheet="connection"}
        ui.onNodeWithTag("website-third-party-cookies").performScrollTo().performClick()
        ui.runOnIdle{assertTrue(c.cookiesFor(tab).acceptThirdPartyCookies(tab.web))}
        val url=server.url("/private").toString();var private:BrowserTab?=null
        ui.runOnIdle{c.sheet="";private=c.newTab(url,true)}
        assertFalse(permissions.thirdPartyCookies(private!!))
        ui.runOnIdle{permissions.setThirdPartyCookies(private!!,true);c.closePrivateTabs()}
        ui.runOnIdle{private=c.newTab(url,true)}
        assertFalse(permissions.thirdPartyCookies(private!!))
        assertTrue(permissions.thirdPartyCookies(tab))
    }
    @Test fun fileCaptureOpensTheSystemCameraAndReturnsAFullPhoto(){
        val tab=load()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.CAMERA)
        val position=JSONObject(eval(tab,"(()=>{const r=document.getElementById('photo').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val location=IntArray(2);ui.runOnUiThread{tab.web.getLocationOnScreen(location)}
        device.click(location[0]+(position.getDouble("x")*position.getDouble("dpr")).toInt(),location[1]+(position.getDouble("y")*position.getDouble("dpr")).toInt())
        assertTrue("The camera must open",device.wait(Until.gone(By.pkg(ui.activity.packageName)),10000))
        repeat(2){device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),2000)?.click()}
        val shutter=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/shutter_button"))),10000)
            ?:device.findObject(By.desc(java.util.regex.Pattern.compile("(?i).*shutter.*|.*take photo.*")))
        if(shutter==null){val tree=java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(tree);fail(tree.toString("UTF-8"))}
        shutter!!.click()
        val done=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/(done_button|review_done|btn_done)"))),10000)
            ?:device.findObject(By.desc(java.util.regex.Pattern.compile("(?i)done|accept|confirm")))
        if(done==null){val tree=java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(tree);fail(tree.toString("UTF-8"))}
        device.executeShellCommand("mkdir -p /data/local/tmp/chengjing-ui")
        device.executeShellCommand("screencap -p /data/local/tmp/chengjing-ui/website-photo-capture.png")
        done!!.click()
        ui.waitUntil(15000){eval(tab,"window.uploaded===true")=="true"}
        val photo=JSONObject(eval(tab,"window.photoResult"))
        assertTrue("The web input must receive a full photo",photo.getLong("size")>100)
        assertTrue(photo.getString("type").startsWith("image/"))
        assertTrue("The local server must receive the actual uploaded image",uploadedBytes.get()>photo.getLong("size"))

    }
}
