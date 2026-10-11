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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The real Google client must deliver to a real page while direct providers are silent. */
class PlayServicesLocationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val geo get()=ui.activity.websiteLocation
    private val client by lazy{LocationServices.getFusedLocationProviderClient(ui.activity)}
    private val manager get()=ui.activity.getSystemService(LocationManager::class.java)
    private val providers=mutableListOf<String>()
    private var mocked=false
    private fun eval(js:String):String{
        val done=CountDownLatch(1);val value=AtomicReference("null")
        ui.runOnUiThread{c.active!!.web.evaluateJavascript(js){value.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return value.get()
    }
    @Suppress("DEPRECATION") @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));PreviewFixture(ui).reset()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("cmd location set-location-enabled true")
        device.executeShellCommand("appops set ${ui.activity.packageName} android:mock_location allow")
        automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals("This test requires actual Play Services",ConnectionResult.SUCCESS,GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(ui.activity))
        for(name in listOf("gps","network","fused")){
            manager.addTestProvider(name,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(name,true);providers.add(name)
        }
        Tasks.await(client.setMockMode(true),10,TimeUnit.SECONDS);mocked=true
        ui.runOnIdle{geo.decisions.clearRegular()}
    }
    @After fun cleanup(){
        ui.runOnIdle{c.tabs.toList().forEach{c.closeTab(it.id)};geo.decisions.clearRegular()}
        if(mocked)runCatching{Tasks.await(client.setMockMode(false),10,TimeUnit.SECONDS)}
        providers.forEach{runCatching{manager.removeTestProvider(it)}}
    }
    private fun fix(){
        Tasks.await(client.setMockLocation(Location("fused").apply{
            latitude=25.033;longitude=121.5654;accuracy=12f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
        }),5,TimeUnit.SECONDS)
    }
    private fun allow(){ui.waitUntil(5000){c.prompts.current!=null};ui.onNodeWithText("允許定位").performClick();ui.waitUntil(5000){"play-services-fused" in ui.runOnIdle{geo.activeProviders}}}
    private fun waitForPosition(condition:()->Boolean){
        var next=0L
        ui.waitUntil(20000){if(SystemClock.elapsedRealtime()>=next){fix();next=SystemClock.elapsedRealtime()+500};condition()}
    }
    @Test fun googleFusedPositionReachesWebsiteWhenDirectProvidersAreSilent(){
        PreviewFixture(ui).load("https://fused-location.invalid/","Fused location")
        eval("window.locationResult=null;window.locationError=null;navigator.geolocation.getCurrentPosition(p=>locationResult=p.toJSON(),e=>locationError=e.code,{enableHighAccuracy:true,timeout:12000,maximumAge:30000});true")
        allow();waitForPosition{eval("locationResult?.coords.latitude")=="25.033"}
        assertEquals("null",eval("locationError"));ui.waitUntil(5000){ui.runOnIdle{geo.activeProviders.isEmpty()}}
    }
    @Test fun liveTrashPageFindsNearestBinsWithGoogleFusedPosition(){
        Assume.assumeTrue("Public page is opt-in",InstrumentationRegistry.getArguments().getString("liveLocation")=="true")
        ui.runOnIdle{c.navigate("https://techtarian.com/tools/tpe-trash-can-location/")}
        ui.waitUntil(60000){eval("!!document.querySelector('#tpe-trash-locator [data-locate]') && /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(document.querySelector('#tpe-trash-locator [data-update-date]').textContent.trim())")=="true"}
        eval("document.querySelector('#tpe-trash-locator [data-locate]').scrollIntoView({block:'center'});document.querySelector('#tpe-trash-locator [data-locate]').click();true")
        allow();waitForPosition{eval("document.querySelector('#tpe-trash-locator [data-status-text]').textContent.includes('定位完成')")=="true"}
        assertTrue(eval("document.querySelector('#tpe-trash-locator [data-results-title]').textContent").contains("5"))
        PreviewFixture(ui).screenshot("fused-location-real-trash-page")
    }
}
