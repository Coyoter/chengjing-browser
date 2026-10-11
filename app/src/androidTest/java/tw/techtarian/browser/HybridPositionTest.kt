package tw.techtarian.browser

import android.Manifest
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** A stalled or failed source cannot suppress another source; cached fixes retain their true age. */
class HybridPositionTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private val manager get()=ui.activity.getSystemService(LocationManager::class.java)
    private var request:NativePositionRequest?=null
    private val providers=mutableListOf<String>()
    private val positions=mutableListOf<JSONObject>()
    private val errors=mutableListOf<Int>()
    private class Source:SupplementalPositionSource {
        lateinit var location:(Location)->Unit
        lateinit var unavailable:()->Unit
        lateinit var options:PositionOptions
        var closed=false
        override fun start(options:PositionOptions,onLocation:(Location)->Unit,onUnavailable:()->Unit){this.options=options;location=onLocation;unavailable=onUnavailable}
        override fun close(){closed=true}
    }
    @Suppress("DEPRECATION") @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));PreviewFixture(ui).reset()
        automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(ui.activity.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("cmd location set-location-enabled true")
        device.executeShellCommand("appops set ${ui.activity.packageName} android:mock_location allow")
        for(name in listOf("gps","network","fused")){
            manager.addTestProvider(name,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(name,true);providers.add(name)
        }
    }
    @After fun cleanup(){ui.runOnIdle{request?.close()};providers.forEach{runCatching{manager.removeTestProvider(it)}}}
    private fun fix(age:Long=0)=Location("fused").apply{
        latitude=25.033;longitude=121.5654;accuracy=15f
        time=System.currentTimeMillis()-age;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()-age*1_000_000
    }
    private fun begin(source:Source,options:PositionOptions=PositionOptions(true,12000,30000),watch:Boolean=false){
        ui.runOnIdle{request=NativePositionRequest(ui.activity,options,watch,{},positions::add,{code,_,_->errors.add(code)},source).also{it.start()}}
    }
    @Test fun recentFusedPositionWorksWithoutWaitingForGps(){
        val source=Source();begin(source)
        ui.runOnIdle{source.location(fix(5000));assertEquals(1,positions.size);assertTrue(errors.isEmpty());assertTrue(source.closed);assertTrue(request!!.activeProviders.isEmpty())}
        assertEquals(PositionOptions(true,12000,30000),source.options)
    }
    @Test fun oldFusedPositionIsRejectedAndGpsCanStillWin(){
        val source=Source();begin(source)
        ui.runOnIdle{source.location(fix(120000));assertTrue(positions.isEmpty());assertFalse(source.closed)}
        manager.setTestProviderLocation("gps",fix())
        ui.waitUntil(5000){ui.runOnIdle{positions.size==1}}
        assertTrue(source.closed);assertTrue(errors.isEmpty())
    }
    @Test fun maximumAgeZeroRejectsHistoryFromTheAdditionalSource(){
        val source=Source();begin(source,PositionOptions(true,12000,0))
        ui.runOnIdle{source.location(fix(5000));assertTrue(positions.isEmpty());source.location(fix());assertEquals(1,positions.size)}
    }
    @Test fun failedFusedServiceDoesNotCancelDirectGps(){
        val source=Source();begin(source)
        ui.runOnIdle{source.unavailable();assertTrue("gps" in request!!.activeProviders);assertFalse("play-services-fused" in request!!.activeProviders);assertTrue(errors.isEmpty())}
        manager.setTestProviderLocation("gps",fix())
        ui.waitUntil(5000){ui.runOnIdle{positions.size==1}}
        assertTrue(source.closed)
    }
    @Test fun timeoutAndCancellationRemoveBothSourcesAndIgnoreLateCallbacks(){
        val source=Source();begin(source,PositionOptions(true,100,0))
        ui.waitUntil(5000){ui.runOnIdle{errors==listOf(3)}}
        ui.runOnIdle{assertTrue(source.closed);assertTrue(request!!.activeProviders.isEmpty());source.location(fix());assertTrue(positions.isEmpty())}
    }
    @Test fun watchStopsBothSourcesWhenClosed(){
        val source=Source();begin(source,watch=true)
        ui.runOnIdle{source.location(fix());assertEquals(1,positions.size);assertFalse(source.closed);request!!.close();source.location(fix());assertEquals(1,positions.size);assertTrue(source.closed);assertTrue(request!!.activeProviders.isEmpty())}
    }
}
