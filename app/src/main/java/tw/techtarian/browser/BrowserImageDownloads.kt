package tw.techtarian.browser

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

/** Uses the system download queue; never reports a queued transfer as a saved file. */
internal class BrowserImageDownloads(private val activity:ComponentActivity,private val notice:(String)->Unit) {
    private data class Pending(val url:String,val page:String,val userAgent:String,val cookie:String?,val mime:String?,val disposition:String?,val imageOnly:Boolean)
    private var pending:Pending?=null
    private val permission=activity.registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val request=pending
        pending=null
        if(granted&&request!=null)enqueue(request)
        else if(!granted)notice(bt(R.string.msg_fb43036835f6))
        else notice(bt(R.string.msg_18763274dfce))
    }
    fun download(rawUrl:String,page:String,userAgent:String,cookieHeader:String?=CookieManager.getInstance().getCookie(rawUrl),mimeHint:String?=null,disposition:String?=null,imageOnly:Boolean=true):Long? {
        val url=PageActionPolicy.downloadUrl(rawUrl)
        if(url==null){
            notice(bt(R.string.msg_38116168b889))
            return null
        }
        val request=Pending(url,page,userAgent,cookieHeader,mimeHint,disposition,imageOnly)
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){notice(bt(R.string.msg_d9832eb8413c));return null}
            pending=request
            try{permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}catch(_:IllegalStateException){pending=null;notice(bt(R.string.msg_1022567a481f))}
            return null
        }
        return enqueue(request)
    }
    private fun enqueue(task:Pending):Long? = try {
        val extension=MimeTypeMap.getFileExtensionFromUrl(task.url.substringBefore('?')).lowercase(Locale.ROOT)
        val mime=task.mime?.takeIf{it.isNotBlank()}?:MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf{!task.imageOnly||it.startsWith("image/")}
        val guessed=URLUtil.guessFileName(task.url,task.disposition,mime)
        val name=PageActionPolicy.safeFilename(guessed,UUID.randomUUID().toString().take(8))
        val request=DownloadManager.Request(Uri.parse(task.url))
            .setTitle(name)
            .setDescription(if(task.imageOnly)bt(R.string.msg_067c7812968e)else bt(R.string.msg_f0ffb8d77605))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
        // When the path has no image extension, let the server's Content-Type decide.
        if(mime!=null)request.setMimeType(mime)
        if(task.userAgent.isNotBlank()&&task.userAgent.length<=2048&&task.userAgent.all{it.code in 32..126})request.addRequestHeader("User-Agent",task.userAgent)
        PageActionPolicy.referrer(task.page,task.url)?.let{request.addRequestHeader("Referer",it)}
        task.cookie?.takeIf{it.isNotBlank()&&it.length<=32768&&!it.contains('\r')&&!it.contains('\n')}?.let{request.addRequestHeader("Cookie",it)}
        val id=(activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        notice(bt(R.string.msg_4a9962f4b813))
        id
    }catch(_:Exception){notice(bt(R.string.msg_46248e4d3641));null}
}
