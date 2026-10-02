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
        else activity.controller.notice=if(granted)bt(R.string.msg_8f95e70a3afe)else bt(R.string.msg_fb43036835f6)
    }
    fun initialize(){if(recovery==null)recovery=activity.lifecycleScope.async(Dispatchers.IO){storage.recover()}}
    fun download(tab:BrowserTab,url:String,mime:String?,disposition:String?){
        val request=Request(tab,url,tab.url,tab.navigationGeneration,mime,disposition)
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){activity.controller.notice=bt(R.string.msg_d9832eb8413c);return}
            pending=request;permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }else start(request)
    }
    private fun start(request:Request){
        val c=activity.controller
        fun valid()=request.tab in c.tabs&&request.tab.navigationGeneration==request.generation
        if(!valid()){c.notice=bt(R.string.msg_ae8e0f51f1c5);return}
        if(jobs.size>=2){c.notice=bt(R.string.msg_167523d3b80d);return}
        val id=ids.decrementAndGet();val now=System.currentTimeMillis()
        val source=if(request.tab.incognito)""else PageActionPolicy.shareUrl(request.page).orEmpty()
        var row=DownloadItem(id,DownloadFormat.filename(null,DownloadFormat.mime(request.mime).orEmpty()),source,DownloadFormat.mime(request.mime)?:"application/octet-stream",DownloadManager.STATUS_RUNNING,0,-1,createdAt=now)
        fun update(value:DownloadItem){val index=items.indexOfFirst{it.id==id};if(index>=0)items[index]=value;row=value}
        while(items.size>=20){val index=items.indexOfFirst{it.status!=DownloadManager.STATUS_RUNNING};if(index<0)break;items.removeAt(index)}
        items.add(0,row);c.notice=bt(R.string.msg_57d25840bf76)
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
                ensureActive();check(valid()){bt(R.string.msg_5f464f616c9a)}
                withContext(NonCancellable){output!!.finish();items.removeAll{it.id==id};c.notice=bt(R.string.msg_13491974a87a)}
            }catch(_:TimeoutCancellationException){
                val detail=bt(R.string.msg_e4726e5fe1e3)
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));c.notice=detail
            }catch(cancelled:CancellationException){
                val detail=cancelled.message?.takeIf{it.any{ch->ch.code>127}}?:bt(R.string.msg_639f890d3c20)
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));throw cancelled
            }catch(error:Exception){
                val detail=error.message?.takeIf{it.any{ch->ch.code>127}}?.take(200)?:bt(R.string.msg_5dfa3c652a61)
                update(row.copy(status=DownloadManager.STATUS_FAILED,detail=detail));c.notice=detail
            }finally{
                withContext(NonCancellable){runCatching{output?.abort()};reader.close()}
            }
        }
        jobs[id]=request.tab.id to job;job.invokeOnCompletion{jobs.remove(id)};job.start()
    }
    fun cancel(id:Long){jobs[id]?.second?.cancel()}
    fun cancelFor(tabId:Int){jobs.values.filter{it.first==tabId}.map{it.second}.forEach{it.cancel(CancellationException(bt(R.string.msg_e9038135c8dd)))};if(pending?.tab?.id==tabId)pending=null}
    fun close(){jobs.values.map{it.second}.forEach{it.cancel()};pending=null}
}
