package tw.techtarian.browser

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import java.io.File
import java.util.UUID

/** File inputs can choose existing files or explicitly capture new media. */
internal class WebsiteFiles(private val activity:MainActivity){
    private val c get()=activity.controller
    private data class Pending(val tab:BrowserTab,val page:String,val generation:Long,
        val callback:ValueCallback<Array<Uri>>,val params:WebChromeClient.FileChooserParams,
        var capture:Intent?=null,var output:File?=null,var outputUri:Uri?=null)
    private var pending:Pending?=null
    private var launched=false
    private var permissionInFlight=false
    private val privateFiles=mutableListOf<File>()
    private val directory get()=File(activity.cacheDir,"website-capture").apply{mkdirs()}
    fun initialize(){directory.listFiles()?.filter{it.name.startsWith("private-")||System.currentTimeMillis()-it.lastModified()>86_400_000L}?.forEach{it.delete()}}
    private val result=activity.activityResultRegistry.register("website-files",activity,ActivityResultContracts.StartActivityForResult()){result->
        launched=false
        val p=pending?:return@register
        val uris=if(result.resultCode!=Activity.RESULT_OK||!valid(p))null
        else p.outputUri?.takeIf{p.output?.length()?.let{it>0}==true}?.let{arrayOf(it)}
            ?:WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data)?.filter{it.scheme=="content"}?.toTypedArray()
        finish(p,uris?.takeIf{it.isNotEmpty()})
    }
    private val cameraPermission=activity.activityResultRegistry.register("website-file-camera",activity,ActivityResultContracts.RequestPermission()){
        permissionInFlight=false
        val p=pending?:return@register
        if(it&&valid(p)&&p.capture!=null)launch(p,p.capture!!)else finish(p,null)
    }
    private fun valid(p:Pending)=p.tab in c.tabs&&c.activeId==p.tab.id&&p.tab.url==p.page&&
        p.tab.navigationGeneration==p.generation&&!activity.isDestroyed
    private fun finish(p:Pending,uris:Array<Uri>?){
        if(pending!==p)return
        pending=null
        p.outputUri?.let{activity.revokeUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
        if(uris==null)p.output?.delete()else if(p.tab.incognito)p.output?.let{privateFiles.add(it)}
        p.callback.onReceiveValue(uris)
    }
    private fun launch(p:Pending,intent:Intent){
        if(!valid(p)){finish(p,null);return}
        runCatching{launched=true;result.launch(intent)}.onFailure{
            launched=false;finish(p,null);c.notice=bt(R.string.web_capture_error)
        }
    }
    private fun pick(p:Pending){
        runCatching{p.params.createIntent()}.onSuccess{launch(p,it)}.onFailure{finish(p,null);c.notice=bt(R.string.web_capture_error)}
    }
    private fun capture(p:Pending,type:String){
        if(!valid(p)){finish(p,null);return}
        val intent=when(type){
            "image"->Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "video"->Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            else->Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        }
        if(type!="audio"){
            directory.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86_400_000L}?.forEach{it.delete()}
            val file=File(directory,(if(p.tab.incognito)"private-" else "")+UUID.randomUUID().toString()+if(type=="image")".jpg" else ".mp4")
            val uri=FileProvider.getUriForFile(activity,"${activity.packageName}.images",file)
            p.output=file;p.outputUri=uri
            intent.putExtra(MediaStore.EXTRA_OUTPUT,uri)
            intent.clipData=ClipData.newRawUri("capture",uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        p.capture=intent
        if(type!="audio"&&ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            runCatching{permissionInFlight=true;cameraPermission.launch(Manifest.permission.CAMERA)}.onFailure{permissionInFlight=false;finish(p,null)}
        else launch(p,intent)
    }
    fun choose(tab:BrowserTab,callback:ValueCallback<Array<Uri>>,params:WebChromeClient.FileChooserParams):Boolean{
        if(launched||permissionInFlight){callback.onReceiveValue(null);return true}
        pending?.let{finish(it,null)}
        val p=Pending(tab,tab.url,tab.navigationGeneration,callback,params)
        if(tab.web.selecting||!valid(p)){callback.onReceiveValue(null);return true}
        pending=p
        val accepts=params.acceptTypes.flatMap{it.split(',')}.map{it.trim().lowercase()}.filter{it.isNotEmpty()}
        fun accepts(type:String)=accepts.isEmpty()||accepts.any{it=="*/*"||it.startsWith("$type/")||
            (type=="image"&&it in setOf(".jpg",".jpeg",".png",".webp"))||
            (type=="video"&&it in setOf(".mp4",".webm"))||
            (type=="audio"&&it in setOf(".mp3",".wav",".m4a",".ogg"))}
        val types=listOf("image","video","audio").filter{accepts(it)}
        if(params.isCaptureEnabled&&types.size==1){capture(p,types.single());return true}
        val actions=mutableListOf(BrowserMenuAction(bt(R.string.web_choose_files),Icons.Outlined.FolderOpen){pick(p)})
        if("image" in types)actions.add(BrowserMenuAction(bt(R.string.web_take_photo),Icons.Outlined.PhotoCamera){capture(p,"image")})
        if("video" in types)actions.add(BrowserMenuAction(bt(R.string.web_record_video),Icons.Outlined.Videocam){capture(p,"video")})
        if("audio" in types)actions.add(BrowserMenuAction(bt(R.string.web_record_audio),Icons.Outlined.Mic){capture(p,"audio")})
        if(actions.size==1){pick(p);return true}
        c.prompts.show(BrowserPrompt.Menu(bt(R.string.web_choose_files),LocationOrigin.of(tab.url).orEmpty(),actions,
            tab.id,{valid(p)},onCancel={finish(p,null)}))
        return true
    }
    fun cancelFor(id:Int){pending?.takeIf{it.tab.id==id}?.let{finish(it,null);c.prompts.closeFor(id)}}
    fun clearPrivate(){privateFiles.forEach{it.delete()};privateFiles.clear()}
    fun close(){pending?.let{finish(it,null)};clearPrivate()}
}
