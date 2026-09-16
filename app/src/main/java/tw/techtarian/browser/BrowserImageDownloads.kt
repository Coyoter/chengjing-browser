package tw.techtarian.browser

import android.Manifest
import android.app.AlertDialog
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

/** Shared by image context menus and ordinary downloads; credentials stay in memory. */
internal class BrowserImageDownloads(private val activity:ComponentActivity,private val notice:(String)->Unit) {
    private data class Pending(val url:String,val page:String,val userAgent:String,val cookie:String?,val disposition:String?,val mime:String?)
    private var pending:Pending?=null
    private val permission=activity.registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val request=pending;pending=null
        if(granted&&request!=null)enqueue(request)
        else if(!granted)notice("未允許儲存權限，檔案沒有下載")
        else notice("請重新選擇下載")
    }
    fun download(rawUrl:String,page:String,userAgent:String,source:BrowserTab?=null,disposition:String?=null,mime:String?=null):Long? {
        val url=PageActionPolicy.downloadUrl(rawUrl)
        if(url==null){notice("目前可下載 HTTP／HTTPS 圖片或檔案；內嵌或 blob 暫存圖片尚未支援");return null}
        // Never fetch default-profile cookies for an incognito request.
        val cookie=try{(source?.web?.let{PrivateSession.cookies(it)}?:CookieManager.getInstance()).getCookie(url)}
            catch(_:Exception){notice("無法取得此工作階段的下載授權");return null}
        val request=Pending(url,page,userAgent,cookie,disposition,mime)
        if(source?.incognito==true){
            AlertDialog.Builder(activity).setTitle("保留這個無痕下載？")
                .setMessage("檔案與系統下載項目會保留在裝置上，關閉無痕分頁不會刪除它們。")
                .setNegativeButton("取消",null).setPositiveButton("保留並下載"){_,_->
                    val controller=(activity as? MainActivity)?.controller
                    if(controller!=null&&source in controller.tabs&&source.url==page)requestStorage(request)
                    else notice("無痕分頁已關閉或改變，請重新選擇下載")
                }.show()
            return null
        }
        return requestStorage(request)
    }
    private fun requestStorage(request:Pending):Long? {
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){notice("請先完成上一個下載的儲存授權");return null}
            pending=request
            try{permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}catch(_:IllegalStateException){pending=null;notice("無法開啟儲存授權，請重新嘗試")}
            return null
        }
        return enqueue(request)
    }
    private fun enqueue(task:Pending):Long? = try {
        val extension=MimeTypeMap.getFileExtensionFromUrl(task.url.substringBefore('?')).lowercase(Locale.ROOT)
        val mime=task.mime?.takeIf{it.contains('/')&&it.all{c->c.code in 32..126}}
            ?:MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val name=PageActionPolicy.safeFilename(URLUtil.guessFileName(task.url,task.disposition,mime),UUID.randomUUID().toString().take(8))
        val request=DownloadManager.Request(Uri.parse(task.url)).setTitle(name)
            .setDescription("澄境瀏覽器 · 下載")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
        if(mime!=null)request.setMimeType(mime)
        if(task.userAgent.isNotBlank()&&task.userAgent.length<=2048&&task.userAgent.all{it.code in 32..126})request.addRequestHeader("User-Agent",task.userAgent)
        PageActionPolicy.referrer(task.page,task.url)?.let{request.addRequestHeader("Referer",it)}
        task.cookie?.takeIf{it.isNotBlank()&&it.length<=32768&&!it.contains('\r')&&!it.contains('\n')}?.let{request.addRequestHeader("Cookie",it)}
        val id=(activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        check(id>=0){"系統未建立下載項目"}
        notice("已加入下載佇列；可在三點選單的「下載」查看結果")
        id
    }catch(_:Exception){notice("無法加入下載，請檢查儲存空間及系統下載服務");null}
}
