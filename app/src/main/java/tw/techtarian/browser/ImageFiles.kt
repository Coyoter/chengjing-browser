package tw.techtarian.browser

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal data class ImageAsset(val file:File,val format:ImageFormat,val width:Int,val height:Int,val source:String,val title:String,val private:Boolean,val thumbnail:android.graphics.Bitmap) {
    fun uri(context:Context):Uri {
        check(!private){"無痕預覽必須先確認匯出"}
        return FileProvider.getUriForFile(context,"${context.packageName}.images",file)
    }
}

/** Only a narrow, app-owned directory is exposed; never the entire cache or profile. */
class BrowserImageProvider:FileProvider() {
    override fun getType(uri:Uri):String?=ImageFormat.forExtension(uri.lastPathSegment.orEmpty().substringAfterLast('.'))?.mime?:super.getType(uri)
}

internal class ImageFiles(private val context:Context) {
    private val shared=File(context.cacheDir,"shared-images").apply{mkdirs()}
    private val privateRoot=File(context.cacheDir,"private-images")
    private val privateDirectory=File(privateRoot,UUID.randomUUID().toString())
    private val prefs=context.getSharedPreferences("image-files-v1",Context.MODE_PRIVATE)
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(30,TimeUnit.SECONDS).build()
    companion object {
        const val MAX_BYTES=32*1024*1024
        private const val DAY=86_400_000L
        private val activePrivate=mutableSetOf<String>()
        private val lock=Any()
        fun copyBounded(input:InputStream,output:OutputStream):Long {
            val buffer=ByteArray(32*1024);var count=0L
            while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue
                count+=n;require(count<=MAX_BYTES){"圖片超過 32 MB，請改由原網站下載"};output.write(buffer,0,n)
            }
            return count
        }
    }
    init {synchronized(lock){
        activePrivate.add(privateDirectory.name)
        privateRoot.listFiles()?.filter{it.name !in activePrivate}?.forEach{it.deleteRecursively()}
        trim(shared,128L*1024*1024)
    }}
    private fun trim(directory:File,maximum:Long,keep:File?=null){
        val now=System.currentTimeMillis();val clip=prefs.getString("clipboard-file",null)
        val rows=directory.listFiles()?.filter{it.isFile&&it.extension!="part"}?:return
        rows.filter{it!=keep&&now-it.lastModified()>DAY}.forEach{it.delete()}
        var total=rows.filter{it.exists()}.sumOf{it.length()}
        rows.filter{it.exists()&&it!=keep&&it.name!=clip}.sortedBy{it.lastModified()}.forEach{if(total>maximum){val size=it.length();if(it.delete())total-=size}}
        directory.listFiles()?.filter{it.extension=="part"&&now-it.lastModified()>DAY}?.forEach{it.delete()}
    }
    private fun part(private:Boolean)=synchronized(lock){
        val directory=if(private)privateDirectory else shared
        check(!private||privateDirectory.name in activePrivate){"無痕工作階段已關閉"}
        check(directory.isDirectory||directory.mkdirs())
        File(directory,UUID.randomUUID().toString()+".part")
    }
    private fun complete(file:File,source:String,title:String,suggestion:String,private:Boolean):ImageAsset {
        val format=file.inputStream().use{ImageFormat.sniff(it.readBounded(512))}?:error("取得的內容不是支援的圖片，未儲存檔案")
        var width=0;var height=0
        val checked=ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)){decoder,info,_->
            width=info.size.width;height=info.size.height
            require(width>0&&height>0)
            val scale=min(1.0,256.0/maxOf(width,height))
            decoder.setTargetSize(maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return synchronized(lock){
            check(!private||privateDirectory.name in activePrivate){"無痕工作階段已關閉"}
            val target=File(file.parentFile,format.filename(suggestion,UUID.randomUUID().toString().replace("-","")))
            check(file.renameTo(target)){"圖片暫存失敗"}
            trim(target.parentFile!!,if(private)64L*1024*1024 else 128L*1024*1024,target)
            ImageAsset(target,format,width,height,source,title,private,checked)
        }
    }
    suspend fun fetchHttp(raw:String,page:String,agent:String,private:Boolean,cookies:(String)->String?):ImageAsset=withContext(Dispatchers.IO){
        var url=PageActionPolicy.downloadUrl(raw)?:error("圖片網址無法使用")
        val file=part(private)
        try{
            repeat(6){
                val request=Request.Builder().url(url).header("Accept","image/webp,image/*,*/*;q=0.8")
                if(private)request.tag(String::class.java,privateDirectory.name)
                if(agent.length<=2048&&agent.all{it.code in 32..126})request.header("User-Agent",agent)
                PageActionPolicy.referrer(page,url)?.let{request.header("Referer",it)}
                cookies(url)?.takeIf{it.length<=32768&&it.none{c->c=='\r'||c=='\n'}}?.let{request.header("Cookie",it)}
                client.newCall(request.build()).execute().use{response->
                    if(response.code in listOf(301,302,303,307,308)){
                        url=response.header("Location")?.let{response.request.url.resolve(it)?.toString()}?.let{PageActionPolicy.downloadUrl(it)}?:error("圖片轉址無法使用")
                    }else{
                        check(response.isSuccessful){"圖片下載失敗（HTTP ${response.code}），未儲存檔案"}
                        val body=response.body?:error("圖片沒有內容")
                        require(body.contentLength()<=MAX_BYTES){"圖片超過 32 MB，請改由原網站下載"}
                        file.outputStream().use{out->body.byteStream().use{copyBounded(it,out)}}
                        currentCoroutineContext().ensureActive()
                        val suggestion=android.webkit.URLUtil.guessFileName(url,response.header("Content-Disposition"),response.header("Content-Type"))
                        return@withContext complete(file,raw,"",suggestion,private)
                    }
                }
            }
            error("圖片轉址次數過多，未儲存檔案")
        }finally{file.delete()}
    }
    suspend fun fromBytes(bytes:ByteArray,source:String,title:String,private:Boolean):ImageAsset=withContext(Dispatchers.IO){
        require(bytes.size<=MAX_BYTES){"圖片超過 32 MB"}
        val file=part(private)
        try{file.writeBytes(bytes);currentCoroutineContext().ensureActive();complete(file,source,title,"image",private)}finally{file.delete()}
    }
    suspend fun export(asset:ImageAsset):ImageAsset {
        if(!asset.private)return asset
        return withContext(Dispatchers.IO){
            val file=part(false)
            try{file.outputStream().use{out->asset.file.inputStream().use{copyBounded(it,out)}};complete(file,asset.source,asset.title,asset.file.name,false)}finally{file.delete()}
        }
    }
    fun pinClipboard(asset:ImageAsset){prefs.edit().putString("clipboard-file",asset.file.name).apply()}
    fun clearShared(){client.dispatcher.runningCalls().filter{it.request().tag(String::class.java)==null}.forEach{it.cancel()};synchronized(lock){shared.listFiles()?.forEach{it.delete()};prefs.edit().remove("clipboard-file").apply()}}
    fun clearPrivate(){client.dispatcher.runningCalls().filter{it.request().tag(String::class.java)==privateDirectory.name}.forEach{it.cancel()};synchronized(lock){privateDirectory.deleteRecursively()}}
    fun close(){client.dispatcher.cancelAll();synchronized(lock){activePrivate.remove(privateDirectory.name);privateDirectory.deleteRecursively()}}
}
