package tw.techtarian.browser

import android.Manifest
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WebsiteLocationTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val location get()=ui.activity.websiteLocation
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val f get()=PreviewFixture(ui)
    private var mockRunning=false
    private var worker:Thread?=null
    @Before fun setup(){
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        f.reset();ui.runOnIdle{location.decisions.clearRegular();location.clearPrivate();ui.activity.applyAppearance("light")}
        device.executeShellCommand("cmd location set-location-enabled true")
    }
    @After fun cleanup(){
        mockRunning=false;worker?.join(1000)
        runCatching{ui.activity.getSystemService(LocationManager::class.java).removeTestProvider(LocationManager.GPS_PROVIDER)}
        ui.runOnIdle{c.prompts.cancel();location.decisions.clearRegular();c.closePrivateTabs();c.sheet=""}
    }
    private fun eval(tab:BrowserTab,script:String):String{
        val done=CountDownLatch(1);val value=AtomicReference("")
        ui.runOnUiThread{tab.web.evaluateJavascript(script){value.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return value.get()
    }
    private fun request(tab:BrowserTab){
        eval(tab,"window.geoResult=null;navigator.geolocation.getCurrentPosition(p=>window.geoResult={latitude:p.coords.latitude,longitude:p.coords.longitude},e=>window.geoResult={error:e.code},{enableHighAccuracy:true,timeout:20000,maximumAge:0});true")
    }
    private fun result(tab:BrowserTab):JSONObject{
        ui.waitUntil(25000){eval(tab,"window.geoResult")!="null"}
        return JSONObject(eval(tab,"window.geoResult"))
    }
    @Suppress("DEPRECATION") private fun mockGps(){
        device.executeShellCommand("appops set ${ui.activity.packageName} android:mock_location allow")
        val manager=ui.activity.getSystemService(LocationManager::class.java)
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
        mockRunning=true
        worker=thread(name="qa-location-fixture"){
            while(mockRunning){
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply{
                    latitude=25.033;longitude=121.5654;accuracy=5f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                })
                Thread.sleep(250)
            }
        }
    }
    @Test fun aWebsiteAndAndroidPermissionAreBothRequiredBeforeCoordinatesReachThePage(){
        val tab=f.load("https://location-qa.invalid/page","Location fixture")
        request(tab);ui.waitUntil(5000){c.prompts.current!=null}
        ui.onNodeWithText("暫不允許").performClick();assertEquals(1,result(tab).getInt("error"))
        assertEquals(LocationChoice.ASK,location.choice(tab))
        request(tab);ui.waitUntil(5000){c.prompts.current!=null};f.screenshot("location-prompt-light")
        ui.onNodeWithText("允許定位").performClick()
        if(!location.hasPermission()){
            val deny=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_deny_button"))),10000)
            assertNotNull("Android must allow declining phone location permission",deny);deny!!.click()
            assertEquals(1,result(tab).getInt("error"));assertEquals(LocationChoice.ASK,location.choice(tab))
            request(tab);ui.waitUntil(5000){c.prompts.current!=null};ui.onNodeWithText("允許定位").performClick()
            val allow=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),10000)
            assertNotNull("Android must request the phone's location permission",allow);allow!!.click()
        }
        ui.waitUntil(5000){location.hasPermission()};mockGps()
        val coordinates=result(tab);assertFalse(coordinates.toString(),coordinates.has("error"))
        assertEquals(25.033,coordinates.getDouble("latitude"),0.1);assertEquals(121.5654,coordinates.getDouble("longitude"),0.1)
        assertEquals(LocationChoice.ALLOW,location.choice(tab))
        assertEquals(LocationChoice.ALLOW,LocationDecisions(ui.activity).get("https://location-qa.invalid",false))
        ui.runOnIdle{c.sheet="connection"};ui.onNodeWithTag("location-choice:BLOCK").performScrollTo().performClick()
        ui.runOnIdle{c.sheet=""}
        // Load a deterministic replacement document after the panel reloads the old one.
        ui.runOnUiThread{tab.web.loadDataWithBaseURL("https://location-qa.invalid/page","<title>Blocked fixture</title>","text/html","UTF-8",null)}
        ui.waitUntil(10000){tab.title=="Blocked fixture"&&tab.pendingUrl.isEmpty()}
        request(tab);assertEquals(1,result(tab).getInt("error"));assertNull(c.prompts.current)
    }
    @Test fun bPrivatePermissionIsIsolatedAndClearedWithTheLastPrivateTab(){
        val tab=f.load("https://location-qa.invalid/private","Private location",true)
        val done=AtomicReference<Boolean?>()
        ui.runOnIdle{location.request(tab,"https://location-qa.invalid/"){_,allowed,remember->assertFalse(remember);done.set(allowed)}}
        ui.onNodeWithText("允許定位").performClick();ui.waitUntil(5000){done.get()!=null};assertTrue(done.get()!!)
        assertEquals(LocationChoice.ASK,location.decisions.get("https://location-qa.invalid",false))
        assertEquals(LocationChoice.ASK,LocationDecisions(ui.activity).get("https://location-qa.invalid",true))
        ui.runOnIdle{c.closePrivateTabs()};assertEquals(LocationChoice.ASK,location.decisions.get("https://location-qa.invalid",true))
    }
    @Test fun cNavigationAndOtherOriginsCannotInheritAPendingApproval(){
        val tab=f.load("https://location-qa.invalid/stale","Stale request")
        ui.runOnIdle{ui.activity.applyAppearance("dark")}
        val denied=mutableListOf<Boolean>()
        ui.runOnIdle{
            location.request(tab,"https://unrelated.invalid"){_,allow,_->denied.add(allow)}
            location.request(tab,"http://location-qa.invalid"){_,allow,_->denied.add(allow)}
        }
        assertEquals(listOf(false,false),denied);assertNull(c.prompts.current)
        ui.runOnIdle{location.request(tab,"https://location-qa.invalid"){_,allow,_->denied.add(allow)}}
        f.screenshot("location-prompt-dark")
        ui.runOnIdle{c.closeTab(tab.id)};assertEquals(listOf(false,false,false),denied);assertNull(c.prompts.current)
    }
}
