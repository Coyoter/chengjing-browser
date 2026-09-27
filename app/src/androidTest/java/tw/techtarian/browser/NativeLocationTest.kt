package tw.techtarian.browser

import android.Manifest
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Exercises the app's own OS registrations, with WebView's original JS methods made unusable. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NativeLocationTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val geo get()=ui.activity.websiteLocation
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val manager get()=ui.activity.getSystemService(LocationManager::class.java)
    private val providers=mutableListOf<String>()
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));PreviewFixture(ui).reset()
        ui.runOnIdle{geo.decisions.clearRegular();geo.clearPrivate()}
        device.executeShellCommand("cmd location set-location-enabled true")
        device.executeShellCommand("appops set ${ui.activity.packageName} android:mock_location allow")
    }
    @After fun cleanup(){
        ui.runOnIdle{c.tabs.toList().forEach{c.closeTab(it.id)};geo.decisions.clearRegular();c.sheet=""}
        providers.forEach{runCatching{manager.removeTestProvider(it)}}
    }
    private fun permission(fine:Boolean=true){
        automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        if(fine)automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
    }
    @Suppress("DEPRECATION") private fun provider(name:String){
        manager.addTestProvider(name,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(name,true);providers.add(name)
    }
    private fun fix(name:String,latitude:Double=25.033,age:Long=0){
        manager.setTestProviderLocation(name,Location(name).apply{
            this.latitude=latitude;longitude=121.5654;accuracy=5f;time=System.currentTimeMillis()-age;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()-age*1_000_000
        })
    }
    private fun page(private:Boolean=false):BrowserTab{
        val tab=PreviewFixture(ui).load("https://native-location.invalid/page","Native location",private)
        eval(tab,"Object.getPrototypeOf(navigator.geolocation).getCurrentPosition=()=>{};Object.getPrototypeOf(navigator.geolocation).watchPosition=()=>0;true")
        assertTrue(geo.nativeAttached(tab.id));assertTrue(eval(tab,"!!window.__chengjingLocationAccess")=="true")
        return tab
    }
    private fun eval(tab:BrowserTab,js:String):String{
        val done=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(js){result.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    private fun request(tab:BrowserTab,watch:Boolean=false,timeout:Int=8000){
        eval(tab,"window.points=[];window.geoError=null;window.watchId=navigator.geolocation.${if(watch)"watchPosition"else"getCurrentPosition"}(p=>points.push(p.toJSON()),e=>geoError=e.code,{enableHighAccuracy:true,maximumAge:0,timeout:$timeout});true")
    }
    private fun allow(){
        try{ui.waitUntil(5000){c.prompts.current!=null}}catch(error:Throwable){
            val state=ui.runOnIdle{"tab="+c.active!!.url+"; web="+c.active!!.web.url+"; allowed="+geo.canUse(c.active!!,LocationOrigin.of(c.active!!.url)!!)}
            throw AssertionError("No permission prompt: "+geo.status+"; access="+eval(c.active!!,"window.__chengjingLocationAccess")+"; error="+eval(c.active!!,"geoError")+"; "+state+"; method="+eval(c.active!!,"navigator.geolocation.getCurrentPosition.toString()"),error)
        }
        ui.onNodeWithText("允許定位").performClick()
    }
    private fun activeProviders()=ui.runOnIdle{geo.activeProviders}
    private fun awaiting(name:String){ui.waitUntil(5000){name in activeProviders()}}
    private fun received(tab:BrowserTab){ui.waitUntil(10000){eval(tab,"points.length").toInt()>0}}
    @Test fun aCoarsePermissionDoesNotRequirePrecisePermission(){
        assertFalse("This fixture must start without precise permission",androidx.core.content.ContextCompat.checkSelfPermission(ui.activity,Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        permission(false);provider(LocationManager.NETWORK_PROVIDER);provider(LocationManager.GPS_PROVIDER)
        val tab=page();request(tab);allow();awaiting(LocationManager.NETWORK_PROVIDER)
        fix(LocationManager.NETWORK_PROVIDER);received(tab)
        val point=JSONObject(eval(tab,"points[0]"));assertEquals(25.033,point.getJSONObject("coords").getDouble("latitude"),.1)
        ui.waitUntil(5000){activeProviders().isEmpty()}
        assertFalse(androidx.core.content.ContextCompat.checkSelfPermission(ui.activity,Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    @Test fun bFreshFixUsesAndroidWhileWebViewsProviderIsDisabled(){
        permission();provider(LocationManager.GPS_PROVIDER)
        val tab=page();val before=geo.registrationCount;request(tab);allow();awaiting(LocationManager.GPS_PROVIDER)
        assertTrue("Native LocationManager must be registered, not merely a permission callback",geo.registrationCount>before)
        val dump=device.executeShellCommand("dumpsys location")
        assertTrue(dump.contains(ui.activity.packageName));assertTrue(dump.contains("gps"))
        fix(LocationManager.GPS_PROVIDER,age=120_000);ui.waitForIdle();assertEquals("0",eval(tab,"points.length"))
        fix(LocationManager.GPS_PROVIDER);received(tab)
        assertEquals(25.033,JSONObject(eval(tab,"points[0]")).getJSONObject("coords").getDouble("latitude"),.0001)
        ui.waitUntil(5000){activeProviders().isEmpty()}
    }
    @Test fun cWatchStopsInBackgroundResumesAndClearWatchReleasesTheService(){
        permission();provider(LocationManager.GPS_PROVIDER);val tab=page();request(tab,true);allow();awaiting("gps")
        fix("gps");received(tab)
        ui.runOnIdle{geo.pause()};assertTrue(activeProviders().isEmpty())
        val count=eval(tab,"points.length");fix("gps",25.034);ui.waitForIdle();assertEquals(count,eval(tab,"points.length"))
        ui.runOnIdle{geo.resume()};awaiting("gps");fix("gps",25.035)
        ui.waitUntil(5000){eval(tab,"points.length").toInt()>count.toInt()}
        eval(tab,"navigator.geolocation.clearWatch(watchId);true");ui.waitUntil(5000){activeProviders().isEmpty()}
    }
    @Test fun dTimeoutDoesNotLeaveGpsRunningOrReturnAnOldPosition(){
        permission();provider("gps");val tab=page();request(tab,timeout=500);allow()
        ui.waitUntil(5000){eval(tab,"geoError")=="3"};assertTrue(activeProviders().isEmpty());assertEquals("0",eval(tab,"points.length"))
    }
    @Test fun eNavigationAndRevocationStopNativeRequests(){
        permission();provider("gps");val tab=page(true);request(tab,true);allow();awaiting("gps")
        ui.runOnIdle{geo.setChoice(tab,LocationChoice.BLOCK)};assertTrue(activeProviders().isEmpty())
        ui.runOnIdle{c.closePrivateTabs()};assertTrue(activeProviders().isEmpty())
        assertEquals(LocationChoice.ASK,geo.decisions.get("https://native-location.invalid",true))
    }
    @Test fun fFramesAndPermissionsPolicyCannotBypassNativeConsent(){
        permission();provider("gps");val tab=page();val before=geo.registrationCount
        eval(tab,"(()=>{const f=document.createElement('iframe');f.srcdoc=\"<script>setTimeout(()=>{ChengJingLocation.postMessage(JSON.stringify({op:'get',id:333,doc:JSON.parse(parent.__chengjingLocationAccess).doc}));parent.frameAttempt=true;},0)<\\/script>\";document.body.append(f);return true;})()")
        ui.waitUntil(5000){eval(tab,"window.frameAttempt===true")=="true"}
        ui.waitForIdle();assertNull(c.prompts.current);assertEquals(before,geo.registrationCount)
        val server=MockWebServer()
        try{
            server.enqueue(MockResponse().setHeader("Content-Type","text/html").setHeader("Permissions-Policy","geolocation=()").setBody("<title>Location policy blocked</title>"));server.start()
            val url=server.url("/policy").toString();ui.runOnIdle{c.navigate(url)}
            ui.waitUntil(10000){tab.title=="Location policy blocked"}
            request(tab);ui.waitUntil(5000){eval(tab,"geoError")=="1"}
            eval(tab,"ChengJingLocation.postMessage(JSON.stringify({op:'get',id:444,doc:JSON.parse(__chengjingLocationAccess).doc}));true")
            ui.waitForIdle();assertNull(c.prompts.current);assertEquals(before,geo.registrationCount);assertTrue(activeProviders().isEmpty())
        }finally{server.shutdown()}
    }
    @Test fun liveTrashPageFindsNearbyBins(){
        Assume.assumeTrue("Public-page check is opt-in",InstrumentationRegistry.getArguments().getString("liveLocation")=="true")
        permission();provider("gps")
        ui.runOnIdle{c.navigate("https://techtarian.com/tools/tpe-trash-can-location/")}
        val tab=c.active!!
        ui.waitUntil(60000){eval(tab,"!!document.querySelector('#tpe-trash-locator [data-locate]') && /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(document.querySelector('#tpe-trash-locator [data-update-date]').textContent.trim())")=="true"}
        fix("gps")
        eval(tab,"document.querySelector('#tpe-trash-locator [data-locate]').scrollIntoView({block:'center'});document.querySelector('#tpe-trash-locator [data-locate]').click();true")
        allow()
        var nextFix=0L
        ui.waitUntil(20000){
            if(SystemClock.elapsedRealtime()>=nextFix){fix("gps");nextFix=SystemClock.elapsedRealtime()+500}
            eval(tab,"document.querySelector('#tpe-trash-locator [data-status-text]').textContent.includes('定位完成')")=="true"
        }
        assertTrue(eval(tab,"document.querySelector('#tpe-trash-locator [data-results-title]').textContent").contains("5"))
        assertTrue(geo.status.contains("已取得位置"))
        PreviewFixture(ui).screenshot("native-location-real-trash-page")
    }
}
