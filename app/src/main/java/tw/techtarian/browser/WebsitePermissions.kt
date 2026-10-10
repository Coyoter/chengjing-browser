package tw.techtarian.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

internal enum class WebsiteResource(val web:String,val labelResource:Int,val androidPermission:String?=null){
    CAMERA(PermissionRequest.RESOURCE_VIDEO_CAPTURE,R.string.web_camera,Manifest.permission.CAMERA),
    MICROPHONE(PermissionRequest.RESOURCE_AUDIO_CAPTURE,R.string.web_microphone,Manifest.permission.RECORD_AUDIO),
    PROTECTED_MEDIA(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,R.string.web_protected_media),
    MIDI(PermissionRequest.RESOURCE_MIDI_SYSEX,R.string.web_midi);
    val label get()=bt(labelResource)
    val defaultChoice get()=if(this==PROTECTED_MEDIA)LocationChoice.ALLOW else LocationChoice.ASK
    companion object{fun of(value:String)=entries.find{it.web==value}}
}

/** Exact-origin grants remain on this device; private grants live only in memory. */
internal class WebsitePermissionDecisions(context:Context){
    private val prefs=context.getSharedPreferences("website-permissions-v1",Context.MODE_PRIVATE)
    private val privateChoices=mutableMapOf<String,LocationChoice>()
    private fun key(origin:String,resource:WebsiteResource)="${resource.name}|$origin"
    fun get(origin:String,resource:WebsiteResource,incognito:Boolean):LocationChoice{
        val key=key(origin,resource)
        return if(incognito)privateChoices[key]?:resource.defaultChoice
        else runCatching{LocationChoice.valueOf(prefs.getString(key,null).orEmpty())}.getOrDefault(resource.defaultChoice)
    }
    fun set(origin:String,resource:WebsiteResource,incognito:Boolean,choice:LocationChoice){
        require(LocationOrigin.of(origin)==origin)
        val key=key(origin,resource)
        if(incognito)privateChoices[key]=choice
        else check(prefs.edit().putString(key,choice.name).commit())
    }
    fun cookies(origin:String,incognito:Boolean)=if(incognito)privateChoices["COOKIES|$origin"]==LocationChoice.ALLOW else prefs.getBoolean("COOKIES|$origin",false)
    fun setCookies(origin:String,incognito:Boolean,allow:Boolean){
        if(incognito)privateChoices["COOKIES|$origin"]=if(allow)LocationChoice.ALLOW else LocationChoice.BLOCK
        else check(prefs.edit().putBoolean("COOKIES|$origin",allow).commit())
    }
    fun clearPrivate(){privateChoices.clear()}
    fun clearRegular(){check(prefs.edit().clear().commit())}
}

internal class WebsitePermissions(private val activity:MainActivity){
    private val c get()=activity.controller
    val decisions by lazy{WebsitePermissionDecisions(activity)}
    private data class Pending(val tab:BrowserTab,val request:PermissionRequest,val origin:String,
        val page:String,val generation:Long,val resources:List<WebsiteResource>,var done:Boolean=false)
    private var pending:Pending?=null
    private var runtimeInFlight=false
    private val runtime=activity.activityResultRegistry.register("website-media",activity,ActivityResultContracts.RequestMultiplePermissions()){
        runtimeInFlight=false
        val p=pending?:return@register
        if(!valid(p)){finish(p,emptyList());return@register}
        val allowed=p.resources.filter{resource->
            decisions.get(p.origin,resource,p.tab.incognito)!=LocationChoice.BLOCK&&hasPermission(resource)
        }
        p.resources.filter{it.androidPermission!=null}.forEach{
            decisions.set(p.origin,it,p.tab.incognito,if(it in allowed)LocationChoice.ALLOW else LocationChoice.BLOCK)
        }
        c.revision++;finish(p,allowed)
    }
    fun hasPermission(resource:WebsiteResource)=resource.androidPermission?.let{
        ContextCompat.checkSelfPermission(activity,it)==PackageManager.PERMISSION_GRANTED
    }?:true
    private fun valid(p:Pending)=!p.done&&p.tab in c.tabs&&p.tab.id==c.activeId&&
        p.tab.navigationGeneration==p.generation&&p.tab.url==p.page&&!p.tab.web.selecting&&
        LocationOrigin.of(p.page)!=null&&p.tab.error.isEmpty()&&p.tab.certificateWarning.isBlank()&&
        !c.store.certificateException(p.page)&&!c.store.certificateException(p.origin)&&
        !activity.isDestroyed
    private fun finish(p:Pending,allowed:List<WebsiteResource>,notifyWeb:Boolean=true){
        if(p.done)return
        p.done=true
        if(pending===p)pending=null
        if(notifyWeb)runCatching{
            if(allowed.isEmpty())p.request.deny()else p.request.grant(allowed.map{it.web}.toTypedArray())
        }
    }
    private fun grant(p:Pending,asked:List<WebsiteResource>){
        if(!valid(p)){finish(p,emptyList());return}
        // A site's choice never bypasses Android's camera/microphone permission.
        asked.forEach{decisions.set(p.origin,it,p.tab.incognito,LocationChoice.ALLOW)}
        c.revision++
        val allowed=p.resources.filter{decisions.get(p.origin,it,p.tab.incognito)==LocationChoice.ALLOW}
        val missing=allowed.filterNot{hasPermission(it)}.mapNotNull{it.androidPermission}.distinct()
        if(missing.isEmpty())finish(p,allowed)
        else{
            runtimeInFlight=true
            runCatching{runtime.launch(missing.toTypedArray())}.onFailure{runtimeInFlight=false;finish(p,emptyList())}
        }
    }
    fun request(tab:BrowserTab,request:PermissionRequest){
        val origin=LocationOrigin.of(request.origin.toString())
        val resources=request.resources.mapNotNull{WebsiteResource.of(it)}.distinct()
        if(origin==null||resources.isEmpty()||runtimeInFlight||!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){
            request.deny();return
        }
        val p=Pending(tab,request,origin,tab.url,tab.navigationGeneration,resources)
        if(!valid(p)){request.deny();return}
        pending?.let{cancelFor(it.tab.id)}
        pending=p
        val asked=resources.filter{decisions.get(origin,it,tab.incognito)==LocationChoice.ASK}
        if(asked.isEmpty()){grant(p,emptyList());return}
        c.prompts.confirm(bt(R.string.web_permissions),bt(R.string.web_permission_prompt,origin,asked.joinToString("、"){it.label}),
            bt(R.string.msg_0bffd418918b),owner=tab.id,valid={valid(p)},cancelLabel=bt(R.string.msg_454b7b66304a),
            onCancel={
                // A stale/navigation cancellation must not silently block another document.
                if(valid(p))asked.forEach{decisions.set(origin,it,tab.incognito,LocationChoice.BLOCK)}
                c.revision++;finish(p,emptyList())
            },confirm={grant(p,asked)})
    }
    fun canceled(request:PermissionRequest){
        pending?.takeIf{it.request===request}?.let{p->finish(p,emptyList(),notifyWeb=false);c.prompts.closeFor(p.tab.id)}
    }
    fun cancelFor(id:Int){
        pending?.takeIf{it.tab.id==id}?.let{p->finish(p,emptyList());c.prompts.closeFor(id)}
    }
    fun choice(tab:BrowserTab,resource:WebsiteResource)=LocationOrigin.of(tab.url)?.let{decisions.get(it,resource,tab.incognito)}?:LocationChoice.BLOCK
    fun setChoice(tab:BrowserTab,resource:WebsiteResource,choice:LocationChoice){
        val origin=LocationOrigin.of(tab.url)?:return
        decisions.set(origin,resource,tab.incognito,choice);c.revision++
        c.tabs.filter{it.incognito==tab.incognito&&LocationOrigin.of(it.url)==origin}.forEach{cancelFor(it.id);if(!it.lazyRestore)it.web.reload()}
    }
    fun thirdPartyCookies(tab:BrowserTab)=LocationOrigin.of(tab.url)?.let{decisions.cookies(it,tab.incognito)}?:false
    fun setThirdPartyCookies(tab:BrowserTab,allow:Boolean){
        val origin=LocationOrigin.of(tab.url)?:return
        decisions.setCookies(origin,tab.incognito,allow);c.revision++
        c.tabs.filter{it.incognito==tab.incognito&&LocationOrigin.of(it.url)==origin}.forEach{
            c.cookiesFor(it).setAcceptThirdPartyCookies(it.web,allow)
            if(!it.lazyRestore)it.web.reload()
        }
    }
    fun clearPrivate(){decisions.clearPrivate()}
    fun close(){pending?.let{finish(it,emptyList())};clearPrivate()}
}
