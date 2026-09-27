package tw.techtarian.browser

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.webkit.GeolocationPermissions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class LocationChoice(val label:String){ASK("詢問"),ALLOW("允許"),BLOCK("封鎖")}

internal object LocationOrigin{
    fun of(raw:String):String?{
        val url=raw.toHttpUrlOrNull()?:return null
        if(!url.isHttps&&url.host !in setOf("localhost","127.0.0.1","::1"))return null
        if(url.username.isNotEmpty()||url.password.isNotEmpty())return null
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
    }
}

/** Site decisions are local, exact-origin permissions; they never enter Google sync. */
internal class LocationDecisions(context:Context){
    private val prefs=context.getSharedPreferences("website-location-v1",Context.MODE_PRIVATE)
    private val privateChoices=mutableMapOf<String,LocationChoice>()
    fun get(origin:String,incognito:Boolean):LocationChoice = if(incognito)privateChoices[origin]?:LocationChoice.ASK
        else runCatching{LocationChoice.valueOf(prefs.getString(origin,null).orEmpty())}.getOrDefault(LocationChoice.ASK)
    fun set(origin:String,incognito:Boolean,choice:LocationChoice){
        require(LocationOrigin.of(origin)==origin)
        if(incognito){if(choice==LocationChoice.ASK)privateChoices.remove(origin)else privateChoices[origin]=choice}
        else check(prefs.edit().apply{if(choice==LocationChoice.ASK)remove(origin)else putString(origin,choice.name)}.commit())
    }
    fun clearPrivate(){privateChoices.clear()}
    fun clearRegular(){check(prefs.edit().clear().commit())}
}

internal class WebsiteLocation(private val activity:MainActivity){
    private val c get()=activity.controller
    private var native:NativeWebsiteLocation?=null
    var status by mutableStateOf("尚未要求定位")
        internal set
    internal val activeProviders get()=native?.activeProviders.orEmpty()
    internal val registrationCount get()=native?.registrationCount?:0
    internal fun nativeAttached(id:Int)=native?.attached(id)==true
    fun attach(tab:BrowserTab){(native?:NativeWebsiteLocation(activity,this).also{native=it}).attach(tab)}
    fun detach(id:Int){cancelFor(id);native?.detach(id)}
    fun pause(){native?.pause()}
    fun resume(){native?.resume()}
    fun pauseFor(id:Int){native?.pauseFor(id)}
    fun resumeFor(id:Int){native?.resumeFor(id)}
    val decisions by lazy{LocationDecisions(activity)}
    private data class Request(val tab:BrowserTab,val origin:String,val raw:String,val generation:Long,val callback:GeolocationPermissions.Callback,val native:Boolean=false,var completed:Boolean=false)
    private var pending:Request?=null
    private var runtimeInFlight=false
    private val permission=activity.activityResultRegistry.register("website-location",activity,ActivityResultContracts.RequestMultiplePermissions()){
        runtimeInFlight=false
        val request=pending?:return@register
        if(!valid(request)){finish(request,false);return@register}
        if(!hasPermission()){
            c.notice="手機尚未允許定位；可從網站資訊開啟 App 權限設定。"
            finish(request,false)
        }else grant(request)
    }
    fun hasPermission()=listOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION).any{
        ContextCompat.checkSelfPermission(activity,it)==PackageManager.PERMISSION_GRANTED
    }
    fun locationEnabled()=activity.getSystemService(LocationManager::class.java)?.isLocationEnabled==true
    internal fun canUse(tab:BrowserTab,origin:String)=tab in c.tabs&&c.activeId==tab.id&&LocationOrigin.of(tab.url)==origin&&
        tab.error.isEmpty()&&tab.certificateWarning.isBlank()&&!c.store.certificateException(origin)&&!activity.isDestroyed
    private fun valid(request:Request)=canUse(request.tab,request.origin)&&request.tab.navigationGeneration==request.generation
    private fun finish(request:Request,allow:Boolean){
        if(request.completed)return
        request.completed=true
        if(pending===request)pending=null
        request.callback.invoke(request.raw,allow,false) // App owns both regular/private retention.
    }
    private fun grant(request:Request){
        if(!valid(request)){finish(request,false);return}
        if(!locationEnabled()){
            c.notice="手機定位服務尚未開啟，請從網站資訊開啟定位設定後再試。"
            finish(request,false);return
        }
        if(!hasPermission()){
            runtimeInFlight=true
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        decisions.set(request.origin,request.tab.incognito,LocationChoice.ALLOW);c.revision++
        finish(request,true)
    }
    fun request(tab:BrowserTab,raw:String,callback:GeolocationPermissions.Callback){
        requestPermission(tab,raw,callback,false)
    }
    internal fun authorizeNative(tab:BrowserTab,raw:String,callback:GeolocationPermissions.Callback){requestPermission(tab,raw,callback,true)}
    private fun requestPermission(tab:BrowserTab,raw:String,callback:GeolocationPermissions.Callback,native:Boolean){
        val origin=LocationOrigin.of(raw)
        if(origin==null||runtimeInFlight){callback.invoke(raw,false,false);return}
        val request=Request(tab,origin,raw,tab.navigationGeneration,callback,native)
        if(!valid(request)){callback.invoke(raw,false,false);return}
        pending?.let{cancelPermissionFor(it.tab.id)}
        pending=request
        when(decisions.get(origin,tab.incognito)){
            LocationChoice.BLOCK->finish(request,false)
            LocationChoice.ALLOW->grant(request)
            LocationChoice.ASK->c.prompts.confirm(
                "允許這個網站取得位置？",
                "$origin\n\n網站可使用你的位置提供附近地點等功能。可在網址列的網站資訊變更。"+
                    if(tab.incognito)"\n\n無痕授權只保留到關閉全部無痕分頁。"else"",
                "允許定位",owner=tab.id,valid={valid(request)},cancelLabel="暫不允許",
                onCancel={finish(request,false)},confirm={grant(request)})
        }
    }
    fun cancelFor(id:Int){
        native?.cancelFor(id)
        cancelPermissionFor(id)
    }
    internal fun cancelPermissionFor(id:Int){
        val request=pending?.takeIf{it.tab.id==id}?:return
        finish(request,false);c.prompts.closeFor(id)
    }
    fun hideLegacyPrompt(id:Int){if(pending?.let{it.tab.id==id&&!it.native}==true)cancelPermissionFor(id)}
    fun choice(tab:BrowserTab)=LocationOrigin.of(tab.url)?.let{decisions.get(it,tab.incognito)}?:LocationChoice.BLOCK
    fun setChoice(tab:BrowserTab,choice:LocationChoice){
        val origin=LocationOrigin.of(tab.url)?:return
        decisions.set(origin,tab.incognito,choice);c.revision++
        // Reload every affected document so an existing watch cannot outlive revocation.
        c.tabs.filter{it.incognito==tab.incognito&&LocationOrigin.of(it.url)==origin}.forEach{
            cancelFor(it.id);it.web.reload()
        }
    }
    fun clearPrivate(){decisions.clearPrivate()}
    fun openAppSettings(){activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}
    fun openLocationSettings(){activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}
    fun close(){native?.close();pending?.let{finish(it,false)};decisions.clearPrivate()}
}
