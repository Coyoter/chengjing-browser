package tw.techtarian.browser

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class ImageAction { PREVIEW, NEW_TAB, COPY, DOWNLOAD, SHARE }

internal object ImageSharing {
    fun chooser(context:Context,asset:ImageAsset):Intent {
        val uri=asset.uri(context)
        val send=Intent(Intent.ACTION_SEND).setType(asset.format.mime)
            .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData=ClipData.newUri(context.contentResolver,"圖片",uri)
        return Intent.createChooser(send,"分享圖片").apply{addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=send.clipData}
    }
}

internal class BrowserImageActions(private val activity:MainActivity) {
    internal val files by lazy{ImageFiles(activity)}
    private val permits=Semaphore(2)
    private val privateJobs=mutableSetOf<Job>()
    private val sharedJobs=mutableSetOf<Job>()
    private var pendingSave:ImageAsset?=null
    private val permission=activity.activityResultRegistry.register("image-export-storage",activity,ActivityResultContracts.RequestPermission()){granted->
        val asset=pendingSave;pendingSave=null
        if(granted&&asset!=null)runAction{save(asset)}else notice("未允許儲存權限，圖片沒有下載")
    }
    private val c get()=activity.controller
    private fun notice(text:String){c.notice=text}
    private fun confirmExport(private:Boolean,action:ImageAction,run:()->Unit){
        if(private&&action in setOf(ImageAction.COPY,ImageAction.DOWNLOAD,ImageAction.SHARE)){
            c.prompts.confirm("圖片會保留在無痕瀏覽之外",
                "下載、剪貼簿或接收圖片的 App 可能保留檔案，不會隨無痕分頁清除。","繼續",confirm=run)
        }else run()
    }
    private fun runAction(private:Boolean=false,work:suspend ()->Unit){
        val job=activity.lifecycleScope.launch {
            try{permits.withPermit{work()}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){notice(e.message?.takeIf{it.any{ch->ch.code>127}}?.take(160)?:"無法取得可用的圖片，未儲存檔案。請稍後重新長按。")}
        }
        val jobs=if(private)privateJobs else sharedJobs
        jobs.add(job);job.invokeOnCompletion{jobs.remove(job)}
    }
    fun perform(tab:BrowserTab,source:PageImageSource,action:ImageAction){
        if(tab !in c.tabs)return
        val generation=tab.navigationGeneration
        val private=tab.incognito
        val internal=private&&action in setOf(ImageAction.PREVIEW,ImageAction.NEW_TAB)
        confirmExport(private,action){
            if(tab !in c.tabs||tab.navigationGeneration!=generation||tab.url!=source.page){notice("頁面已變更，請重新長按圖片");return@confirmExport}
            if(action==ImageAction.NEW_TAB&&PageActionPolicy.downloadUrl(source.url)!=null){
                val child=c.newTab(incognito=private)?:return@confirmExport
                child.pendingUrl=source.url
                child.web.loadUrl(source.url,PageActionPolicy.referrer(source.page,source.url)?.let{mapOf("Referer" to it)}?:emptyMap())
                c.sheet="";return@confirmExport
            }
            val cookies=c.cookiesFor(tab);val agent=tab.web.settings.userAgentString
            notice("正在取得圖片…")
            runAction(internal){
                val asset=when {
                    source.url.startsWith("data:")->files.fromBytes(withContext(Dispatchers.Default){PageImageReader.dataBytes(source.url)},source.page,source.title,internal)
                    source.url.startsWith("blob:")->files.fromBytes(PageImageReader.blobBytes(tab,source.url){tab in c.tabs&&tab.navigationGeneration==generation&&tab.url==source.page},source.page,source.title,internal)
                    else->files.fetchHttp(source.url,source.page,agent,internal){cookies.getCookie(it)}.copy(title=source.title)
                }
                if(action!=ImageAction.DOWNLOAD&&
                    (tab !in c.tabs||tab.navigationGeneration!=generation||c.activeId!=tab.id)){
                    if(asset.private)asset.file.delete()
                    error("頁面已變更，請重新長按圖片")
                }
                apply(asset,action,private)
            }
        }
    }
    fun usePrepared(asset:ImageAsset,action:ImageAction){confirmExport(asset.private,action){runAction(asset.private&&action==ImageAction.PREVIEW){
        val export=action in setOf(ImageAction.COPY,ImageAction.DOWNLOAD,ImageAction.SHARE)
        apply(if(export)files.export(asset)else asset,action,asset.private)
    }}}
    private suspend fun apply(asset:ImageAsset,action:ImageAction,private:Boolean){
        when(action){
            ImageAction.PREVIEW->{c.imagePreview=asset;notice("")}
            ImageAction.NEW_TAB->{if(c.newImageTab(asset,private)!=null)notice("已在新分頁開啟暫存圖片")}
            ImageAction.COPY->{
                val uri=asset.uri(activity)
                val clip=ClipData.newUri(activity.contentResolver,"圖片",uri)
                if(private)clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)}
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                files.pinClipboard(asset);notice("已複製圖片，可貼到支援圖片的 App")
            }
            ImageAction.SHARE->{activity.startActivity(ImageSharing.chooser(activity,asset));notice("")}
            ImageAction.DOWNLOAD->{
                if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
                    check(pendingSave==null){"請先完成上一張圖片的儲存授權"}
                    pendingSave=asset;permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }else save(asset)
            }
        }
    }
    private suspend fun save(asset:ImageAsset){SavedImageDownloads(activity).save(asset);notice("圖片已下載至 Downloads/ChengJing，可在「下載」查看")}
    fun clearShared(){sharedJobs.toList().forEach{it.cancel()};sharedJobs.clear();pendingSave=null;if(c.imagePreview?.private==false)c.imagePreview=null;files.clearShared()}
    fun clearPrivate(){privateJobs.toList().forEach{it.cancel()};privateJobs.clear();if(c.imagePreview?.private==true)c.imagePreview=null;files.clearPrivate()}
    fun close(){privateJobs.toList().forEach{it.cancel()};files.close()}
    fun initialize(){files}
}
