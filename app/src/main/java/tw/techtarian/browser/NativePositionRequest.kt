package tw.techtarian.browser

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.json.JSONObject

internal data class PositionOptions(val highAccuracy:Boolean=false,val timeout:Long=60_000,val maximumAge:Long=0){
    companion object{
        fun from(json:JSONObject)=PositionOptions(json.optBoolean("highAccuracy"),json.optLong("timeout",60_000).coerceIn(0,120_000),json.optLong("maximumAge",0).coerceIn(0,4_294_967_295L))
    }
}

/** Explicit Android providers: no WebView or Google Play location dependency. Main-thread owned. */
internal class NativePositionRequest(
    private val context:Context,private val options:PositionOptions,private val watch:Boolean,
    private val onStarted:(Set<String>)->Unit,private val onPosition:(JSONObject)->Unit,
    private val onError:(Int,String,Boolean)->Unit
):AutoCloseable{
    private val manager=context.getSystemService(LocationManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private val registered=linkedSetOf<String>()
    val activeProviders:Set<String> get()=registered.toSet()
    private var closed=false
    private var precise=false
    private var startedNanos=0L
    private var lastFixNanos=0L
    private val timeout=Runnable{
        if(!closed){
            if(!watch)close()
            onError(3,bt(R.string.msg_770b5a444a9c),!watch)
            if(watch&&!closed)armTimeout()
        }
    }
    private val retry=Runnable{if(!closed)register()}
    private val listener=object:LocationListener{
        override fun onLocationChanged(location:Location){
            if(closed||location.elapsedRealtimeNanos<startedNanos||location.elapsedRealtimeNanos<=lastFixNanos)return
            deliver(location)
        }
        override fun onProviderDisabled(provider:String){
            if(!closed&&registered.none{runCatching{manager.isProviderEnabled(it)}.getOrDefault(false)}){
                if(!watch)close()
                onError(2,bt(R.string.msg_2e6933e93990),!watch)
            }
        }
        override fun onProviderEnabled(provider:String){}
        @Deprecated("Required for Android 9") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
    }
    private fun permitted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission") fun start(){
        check(Looper.myLooper()==Looper.getMainLooper())
        startedNanos=SystemClock.elapsedRealtimeNanos()
        precise=permitted(Manifest.permission.ACCESS_FINE_LOCATION)
        if(!permitted(Manifest.permission.ACCESS_FINE_LOCATION)&&!permitted(Manifest.permission.ACCESS_COARSE_LOCATION)){
            close();onError(1,bt(R.string.msg_aa939329dc62),true);return
        }
        val providers=providers()
        if(options.maximumAge>0){
            val candidates=providers.mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}
                .filter{val age=startedNanos-it.elapsedRealtimeNanos;age>=0&&age/1_000_000<=options.maximumAge}
            val cached=if(options.highAccuracy)candidates.filter{it.hasAccuracy()&&it.accuracy.isFinite()&&it.accuracy>=0}
                .minWithOrNull(compareBy<Location>{it.accuracy}.thenByDescending{it.elapsedRealtimeNanos})
                else candidates.maxByOrNull{it.elapsedRealtimeNanos}
            if(cached!=null&&deliver(cached)&&!watch)return
        }
        if(closed)return
        if(options.timeout==0L&&!watch){close();onError(3,bt(R.string.msg_f4e900470989),true);return}
        register();if(!closed)armTimeout()
    }
    private fun providers():List<String>{
        val fine=permitted(Manifest.permission.ACCESS_FINE_LOCATION)
        // Also accept Android's optional fused provider, but always request direct
        // GPS/network sources too: a stalled fused provider cannot suppress them.
        return listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER,"fused")
            .filter{(it!=LocationManager.GPS_PROVIDER||fine)&&runCatching{manager.isProviderEnabled(it)}.getOrDefault(false)}
    }
    @SuppressLint("MissingPermission") private fun register(){
        if(closed)return
        var denied=false
        for(provider in providers())if(provider !in registered){
            try{
                manager.requestLocationUpdates(provider,if(options.highAccuracy)1000 else 5000,0f,listener,Looper.getMainLooper())
                registered.add(provider)
            }catch(_:SecurityException){denied=true}catch(_:RuntimeException){}
        }
        if(registered.isEmpty()){
            if(!watch||denied)close()else main.postDelayed(retry,5000)
            onError(if(denied)1 else 2,if(denied)bt(R.string.msg_aa939329dc62)else bt(R.string.msg_8e6878344350),!watch||denied)
        }else onStarted(activeProviders)
    }
    private fun deliver(location:Location):Boolean{
        if((precise&&!permitted(Manifest.permission.ACCESS_FINE_LOCATION))||
            (!permitted(Manifest.permission.ACCESS_FINE_LOCATION)&&!permitted(Manifest.permission.ACCESS_COARSE_LOCATION))){
            close();onError(1,bt(R.string.msg_356ea0bf2ed4),true);return false
        }
        val lat=location.latitude;val lon=location.longitude;val accuracy=location.accuracy
        if(!lat.isFinite()||lat !in -90.0..90.0||!lon.isFinite()||lon !in -180.0..180.0||!location.hasAccuracy()||!accuracy.isFinite()||accuracy<0)return false
        lastFixNanos=location.elapsedRealtimeNanos
        fun optional(present:Boolean,value:Double):Any=if(present&&value.isFinite())value else JSONObject.NULL
        val coordinates=JSONObject().put("latitude",lat).put("longitude",lon).put("accuracy",accuracy.toDouble())
            .put("altitude",optional(location.hasAltitude(),location.altitude))
            .put("altitudeAccuracy",optional(location.hasVerticalAccuracy(),location.verticalAccuracyMeters.toDouble()))
            .put("heading",optional(location.hasBearing()&&location.hasSpeed()&&location.speed>0,location.bearing.toDouble()))
            .put("speed",optional(location.hasSpeed()&&location.speed>=0,location.speed.toDouble()))
        if(!watch)close()else armTimeout()
        onPosition(JSONObject().put("coords",coordinates).put("timestamp",location.time))
        return true
    }
    private fun armTimeout(){main.removeCallbacks(timeout);main.postDelayed(timeout,if(watch)maxOf(1000,options.timeout)else options.timeout)}
    override fun close(){
        if(closed)return
        closed=true;main.removeCallbacksAndMessages(null)
        // Also remove a transport whose registration reply failed after the service accepted it.
        runCatching{manager.removeUpdates(listener)}
        registered.clear()
    }
}
