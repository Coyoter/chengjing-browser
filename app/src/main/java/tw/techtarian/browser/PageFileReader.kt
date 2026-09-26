package tw.techtarian.browser

import android.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.OutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume

/** Pulls one slice at a time. No callable native JavaScript interface or whole-file base64 copy. */
internal class PageFileReader(private val tab:BrowserTab,private val current:()->Boolean) {
    private val key="__cj_file_"+UUID.randomUUID().toString().replace("-","")
    private var data:InputStream?=null
    private var dataOffset=0L
    private fun checkPage(){check(current()){"來源分頁已關閉或重新載入，下載已停止；請在原頁重試"}}
    private suspend fun js(code:String):String=withContext(Dispatchers.Main.immediate){
        checkPage()
        withTimeout(15000){suspendCancellableCoroutine{continuation->tab.web.evaluateJavascript(code){if(continuation.isActive)continuation.resume(it?:"null")}}}
    }
    data class Info(val size:Long,val mime:String,val name:String)
    suspend fun open(url:String):Info {
        check(DownloadFormat.inline(url))
        if(url.startsWith("data:")){
          // Read only the clicked download name. Never inject a potentially private data payload into page JS.
          val x=tab.web.lastTouchX;val y=tab.web.lastTouchY;val width=tab.web.width.coerceAtLeast(1);val height=tab.web.height.coerceAtLeast(1)
          val name=runCatching{JSONTokener(js("""(()=>{const v=visualViewport;let e=document.elementFromPoint($x/$width*(v?v.width:innerWidth)+(v?v.offsetLeft:0),$y/$height*(v?v.height:innerHeight)+(v?v.offsetTop:0));while(e?.shadowRoot){const child=e.shadowRoot.elementFromPoint($x/$width*innerWidth,$y/$height*innerHeight);if(!child||child===e)break;e=child;}const a=e?.closest('a[download]')||document.activeElement?.closest('a[download]');return a?.href?.startsWith('data:')?(a.getAttribute('download')||'').slice(0,1000):'';})()""")).nextValue() as? String}.getOrNull().orEmpty()
          return withContext(Dispatchers.IO){
            val source=DataDownload(url);var size=0L
            try{source.open().use{input->val buffer=ByteArray(64*1024);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;size+=n;require(size<=DownloadFormat.MAX_PAGE_BYTES){"內嵌檔案過大"}}}}
            catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error("內嵌檔案格式不正確，未儲存檔案")}
            data=source.open();Info(size,source.mime,name)
          }
        }
        val quoted=JSONObject.quote(key)
        js("""(()=>{
            const url=${JSONObject.quote(url)},job={ready:false,error:false,blob:null,chunk:null,controller:new AbortController()};window[$quoted]=job;
            const anchor=Array.from(document.querySelectorAll('a[download]')).find(a=>a.href===url);
            job.name=anchor?.getAttribute('download')||'';
            fetch(url,{signal:job.controller.signal}).then(r=>{if(!r.ok)throw Error();return r.blob()})
              .then(blob=>{if(window[$quoted]===job){job.blob=blob;job.ready=true}}).catch(()=>{job.error=true});return true;
        })()""".trimIndent())
        return withTimeout(60000){
            while(true){
                val state=JSONObject(js("(()=>{const j=window[$quoted];return {ready:!!j?.ready,error:!j||j.error,size:j?.blob?.size||0,mime:j?.blob?.type||'',name:j?.name||''}})()"))
                check(!state.optBoolean("error")){"讀不到這份暫存檔案。請在原頁重試；分段串流或受保護影片需使用網站提供的下載方式"}
                if(state.optBoolean("ready")){
                    val size=state.getLong("size");require(size in 0..DownloadFormat.MAX_PAGE_BYTES){"網頁暫存下載目前上限為 2 GB，請使用網站的直接下載連結"}
                    return@withTimeout Info(size,state.optString("mime"),state.optString("name").take(1000))
                }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }
    suspend fun chunk(offset:Long,count:Int):ByteArray {
        data?.let{input->return withContext(Dispatchers.IO){
            check(offset==dataOffset)
            val result=ByteArray(count);var n=0
            while(n<count){val read=input.read(result,n,count-n);check(read>0){"內嵌檔案不完整"};n+=read}
            dataOffset+=n;result
        }}
        val q=JSONObject.quote(key)
        val started=js("""(()=>{const j=window[$q];if(!j?.blob)return false;j.chunk=null;j.error=false;
            const reader=new FileReader();j.reader=reader;reader.onload=()=>{j.chunk=String(reader.result).split(',')[1]||''};reader.onerror=()=>{j.error=true};reader.onabort=()=>{j.error=true};reader.readAsDataURL(j.blob.slice($offset,${offset+count}));return true;})()""")
        check(started=="true"){"暫存檔案已失效，請重新下載"}
        return withTimeout(15000){
            while(true){
                checkPage()
                val state=JSONObject(js("(()=>{const j=window[$q];if(!j||j.error)return {error:true};if(j.chunk===null)return {};const data=j.chunk;j.chunk=null;return {data};})()"))
                check(!state.optBoolean("error")){"暫存檔案讀取失敗，請重新下載"}
                if(state.has("data")){
                    val data=state.getString("data");require(data.length<=(count+2)/3*4+4){"下載內容大小不正確"}
                    return@withTimeout Base64.decode(data,Base64.DEFAULT).also{check(it.size==count){"下載內容不完整，未保留檔案"}}
                }
                delay(10)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }
    suspend fun copy(size:Long,first:ByteArray,out:OutputStream,progress:(Long)->Unit){
        withContext(Dispatchers.IO){out.write(first)};var offset=first.size.toLong();progress(offset)
        while(offset<size){
            currentCoroutineContext().ensureActive();checkPage()
            val data=chunk(offset,minOf(192*1024L,size-offset).toInt())
            withContext(Dispatchers.IO){out.write(data)};offset+=data.size;progress(offset)
        }
    }
    suspend fun close(){
        data?.let{withContext(NonCancellable+Dispatchers.IO){it.close()};data=null;return}
        withContext(NonCancellable+Dispatchers.Main.immediate){
            if(current())runCatching{withTimeout(1000){js("(()=>{const j=window[${JSONObject.quote(key)}];j?.controller?.abort();if(j?.reader?.readyState===1)j.reader.abort();delete window[${JSONObject.quote(key)}];})()")}}
        }
    }
}
