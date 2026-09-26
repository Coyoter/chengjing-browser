package tw.techtarian.browser

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

internal class BrowserPageDownloads(private val activity:MainActivity) {
    companion object { private val ids=AtomicLong(Long.MIN_VALUE/2) }
    private data class Request(val tab:BrowserTab,val url:String,val page:String,val generation:Long,val mime:String?,val disposition:String?)
    val items=mutableStateListOf<DownloadItem>()
    private val jobs=mutableMapOf<Long,Pair<Int,Job>>()
    private val storage by lazy{PageFileStorage(activity)}
    private var recovery:Deferred<Unit>?=null
    private var pending:Request?=null
    private val permission=activity.activityResultRegistry.register("page-file-storage",activity,ActivityResultContracts.RequestPermission()){granted->
        val value=pending;pending=null
        if(granted&&value!=null)start(value)
        else activity.controller.notice=if(granted)"儲存權限已允許，請重新選擇要下載的檔案"else"未允許儲存權限，檔案沒有下載"
    }
    fun initialize(){if(recovery==null)recovery=activity.lifecycleScope.async(Dispatchers.IO){storage.recover()}}
    fun download(tab:BrowserTab,url:String,mime:String?,disposition:String?){
        val request=Request(tab,url,tab.url,tab.navigationGeneration,mime,disposition)
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){activity.controller.notice="請先完成上一個檔案的儲存授權";return}
            pending=request;permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }else start(request)
    }
    private fun start(request:Request){
        val c=activity.controller
        fun valid()=request.tab in c.tabs&&request.tab.navigationGeneration==request.generation
        if(!valid()){c.notice="來源頁面已變更，請重新下載";return}
        if(jobs.size>=2){c.notice="目前已有兩項暫存檔案正在下載，請稍後再試";return}
        val id=ids.decrementAndGet();val now=System.currentTimeMillis()
        val source=if(request.tab.incognito)""else PageActionPolicy.shareUrl(request.page).orEmpty()
        var row=DownloadItem(id,DownloadFormat.filename(null,DownloadFormat.mime(request.mime).orEmpty()),source,DownloadFormat.mime(request.mime)?:"application/octet-stream",DownloadManager.STATUS_RUNNING,0,-1,createdAt=now)
        fun update(value:DownloadItem){val index=items.indexOfFirst{it.id==id};if(index>=0)items[index]=value;row=value}
        while(items.size>=20){val index=items.indexOfFirst{it.status!=DownloadManager.STATUS_RUNNING};if(index<0)break;items.removeAt(index)}
        items.add(0,row);c.notice="正在下載網頁檔案，可在「下載」查看進度或取消；請保留來源分頁"
        val job=activity.lifecycleScope.launch(start=CoroutineStart.LAZY){
            val reader=PageFileReader(request.tab,::valid);var output:PageFileStorage.Pending?=null
            try{
                initialize();recovery!!.await()
                val info=reader.open(request.url)
                val first=reader.chunk(0,minOf(info.size,512).toInt())
                val mime=DownloadFormat.infer(first,DownloadFormat.mime(info.mime)?:request.mime)
                val suggested=request.disposition?.takeIf{it.contains("filename",ignoreCase=true)}?.let{android.webkit.URLUtil.guessFileName("https://download.invalid/file",it,mime)}?.takeIf{it.isNotBlank()}?:info.name
                val name=DownloadFormat.filename(suggested,mime)
                update(row.copy(title=name,mime=mime,total=info.size))
                output=storage.create(name,mime,source,info.size)
                var last=0L
                reader.copy(info.size,first,output!!.output){done->
                    val time=android.os.SystemClock.elapsedRealtime()
                    if(time-last>=150||done==info.size){update(row.copy(done=done));last=time}
                }
                ensureActive();check(valid()){"來源頁面已變更，下載已停止"}
                withContext(NonCancellable){output!!.finish();items.removeAll{it.id==id};c.notice="已下載至 Downloads/ChengJing，可在「下載」開啟"}
            }catch(_:TimeoutCancellationException){
                val detail="讀取暫存檔案逾時，請保留來源分頁並重新下載"
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));c.notice=detail
            }catch(cancelled:CancellationException){
                val detail=cancelled.message?.takeIf{it.any{ch->ch.code>127}}?:"已取消；未保留不完整檔案"
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));throw cancelled
            }catch(error:Exception){
                val detail=error.message?.takeIf{it.any{ch->ch.code>127}}?.take(200)?:"下載未完成，請檢查儲存空間並重試"
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));c.notice=detail
            }finally{
                withContext(NonCancellable){runCatching{output?.abort()};reader.close()}
            }
        }
        jobs[id]=request.tab.id to job;job.invokeOnCompletion{jobs.remove(id)};job.start()
    }
    fun cancel(id:Long){jobs[id]?.second?.cancel()}
    fun cancelFor(tabId:Int){jobs.values.filter{it.first==tabId}.map{it.second}.forEach{it.cancel(CancellationException("來源分頁已關閉或重新載入，下載已停止；未保留不完整檔案"))};if(pending?.tab?.id==tabId)pending=null}
    fun close(){jobs.values.map{it.second}.forEach{it.cancel()};pending=null}
}
