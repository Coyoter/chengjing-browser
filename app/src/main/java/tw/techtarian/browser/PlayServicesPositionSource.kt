package tw.techtarian.browser

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Optional additional source. Android providers remain active independently. */
internal interface SupplementalPositionSource:AutoCloseable {
    fun start(options:PositionOptions,onLocation:(Location)->Unit,onUnavailable:()->Unit)
}

internal class PlayServicesPositionSource private constructor(
    private val client:FusedLocationProviderClient
):SupplementalPositionSource {
    private var closed=false
    private var callback:LocationCallback?=null

    @SuppressLint("MissingPermission")
    override fun start(options:PositionOptions,onLocation:(Location)->Unit,onUnavailable:()->Unit){
        if(closed)return
        val receiver=object:LocationCallback(){
            override fun onLocationResult(result:LocationResult){
                for(location in result.locations)if(!closed)onLocation(location)
            }
        }
        callback=receiver
        val request=LocationRequest.Builder(
            if(options.highAccuracy)Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if(options.highAccuracy)1000L else 5000L
        ).setMinUpdateIntervalMillis(1000)
            .setMaxUpdateAgeMillis(options.maximumAge)
            // A useful network fix must not wait for an indoor GPS fix.
            .setWaitForAccurateLocation(false).build()
        try{
            client.requestLocationUpdates(request,receiver,Looper.getMainLooper())
                .addOnSuccessListener{if(closed)runCatching{client.removeLocationUpdates(receiver)}}
                .addOnFailureListener{
                    runCatching{client.removeLocationUpdates(receiver)}
                    if(!closed){callback=null;onUnavailable()}
                }
        }catch(_:RuntimeException){
            runCatching{client.removeLocationUpdates(receiver)}
            callback=null;onUnavailable()
        }
    }
    override fun close(){
        if(closed)return
        closed=true
        callback?.let{runCatching{client.removeLocationUpdates(it)}}
        callback=null
    }
    companion object{
        fun create(context:Context):SupplementalPositionSource?=runCatching{
            if(GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)!=ConnectionResult.SUCCESS)null
            else PlayServicesPositionSource(LocationServices.getFusedLocationProviderClient(context))
        }.getOrNull()
    }
}
