package tw.techtarian.browser

import android.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume

internal data class PageImageSource(val url:String,val page:String,val title:String="",val width:Int=0,val height:Int=0)

internal object PageImageReader {
    private suspend fun js(tab:BrowserTab,code:String):String=withContext(Dispatchers.Main.immediate){
        withTimeout(8000){suspendCancellableCoroutine{continuation->
            tab.web.evaluateJavascript(code){if(continuation.isActive)continuation.resume(it?:"null")}
        }}
    }
    /** Match the native hit target; currentSrc is the displayed srcset/picture resource. */
    suspend fun resolve(tab:BrowserTab,native:String,page:String):PageImageSource {
        if(native.length>65536)return PageImageSource(native,page,tab.title)
        val x=tab.web.lastTouchX;val y=tab.web.lastTouchY
        val width=tab.web.width.coerceAtLeast(1);val height=tab.web.height.coerceAtLeast(1)
        val code="""(()=>{
            const native=${JSONObject.quote(native)}, v=window.visualViewport;
            const x=$x/$width*(v?v.width:innerWidth)+(v?v.offsetLeft:0),y=$y/$height*(v?v.height:innerHeight)+(v?v.offsetTop:0);
            let e=document.elementFromPoint(x,y);
            while(e&&e.shadowRoot){const child=e.shadowRoot.elementFromPoint(x,y);if(!child||child===e)break;e=child;}
            let img=e&&e.tagName==='IMG'?e:null;
            if(!img)img=Array.from(document.images).find(i=>i.currentSrc===native||i.src===native);
            if(!img)return null;
            return {url:img.currentSrc||img.src,title:img.alt||document.title,width:img.naturalWidth,height:img.naturalHeight};
        })()""".trimIndent()
        return runCatching{JSONObject(withTimeout(700){js(tab,code)}).let{j->
            val url=j.optString("url").takeIf{it.startsWith("https://")||it.startsWith("http://")||it.startsWith("data:")||it.startsWith("blob:")}?:native
            PageImageSource(url,page,j.optString("title").take(160),j.optInt("width"),j.optInt("height"))
        }}.getOrElse{PageImageSource(native,page,tab.title)}
    }
    fun dataBytes(url:String):ByteArray {
        val comma=url.indexOf(',');require(comma in 5..1024){"內嵌圖片格式無法使用"}
        require(url.length<=ImageFiles.MAX_BYTES*4+1024){"內嵌圖片過大"}
        val data=url.substring(comma+1)
        return if(url.substring(0,comma).contains(";base64",true)){
            require(data.length<=ImageFiles.MAX_BYTES*4/3+4096){"內嵌圖片超過 32 MB"}
            Base64.decode(android.net.Uri.decode(data),Base64.DEFAULT).also{require(it.size<=ImageFiles.MAX_BYTES)}
        }else{
            val out=ByteArrayOutputStream();var i=0
            while(i<data.length){
                if(data[i]=='%'&&i+2<data.length){out.write(data.substring(i+1,i+3).toInt(16));i+=3}
                else {out.write(data[i].toString().toByteArray(Charsets.UTF_8));i++}
                require(out.size()<=ImageFiles.MAX_BYTES){"內嵌圖片超過 32 MB"}
            };out.toByteArray()
        }
    }
    /** Blob URLs only exist inside their creating browser session. No native JS bridge is exposed. */
    suspend fun blobBytes(tab:BrowserTab,url:String,isCurrent:()->Boolean):ByteArray=withTimeout(30_000){
        val key="__cj_image_"+UUID.randomUUID().toString().replace("-","")
        fun checkPage(){check(isCurrent()){ "頁面已變更，請重新長按圖片" }}
        checkPage()
        js(tab,"""(()=>{
            const key=${JSONObject.quote(key)},job={ready:false,error:false,data:''};window[key]=job;
            fetch(${JSONObject.quote(url)}).then(r=>{if(!r.ok)throw Error();return r.blob()})
            .then(b=>{if(b.size>${ImageFiles.MAX_BYTES})throw Error();return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=reject;r.readAsDataURL(b)})})
            .then(s=>{if(window[key]===job){job.data=s.slice(s.indexOf(',')+1);job.ready=true}})
            .catch(()=>{job.error=true});return true;
        })()""".trimIndent())
        try{
            var length=0
            while(true){
                checkPage()
                val state=JSONObject(js(tab,"(()=>{const j=window[${JSONObject.quote(key)}];return {ready:!!j?.ready,error:!j||j.error,length:j?.data.length||0}})()"))
                check(!state.optBoolean("error")){"無法讀取這張暫存圖片，請在原頁重新長按"}
                if(state.optBoolean("ready")){length=state.getInt("length");break}
                delay(60)
            }
            require(length<=ImageFiles.MAX_BYTES*4/3+4){"圖片超過 32 MB"}
            val out=ByteArrayOutputStream()
            for(offset in 0 until length step 65536){
                checkPage()
                val text=JSONTokener(js(tab,"window[${JSONObject.quote(key)}].data.slice($offset,${offset+65536})")).nextValue() as String
                out.write(Base64.decode(text,Base64.DEFAULT));require(out.size()<=ImageFiles.MAX_BYTES)
            }
            out.toByteArray()
        }finally{
            withContext(NonCancellable){if(isCurrent())runCatching{js(tab,"delete window[${JSONObject.quote(key)}]")}}
        }
    }
}
