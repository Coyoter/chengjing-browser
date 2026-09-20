package tw.techtarian.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SiteIcons(context:Context){
    private val directory=File(context.cacheDir,"site-icons").apply{mkdirs()}
    private data class CachedIcon(val bitmap:Bitmap,val savedAt:Long)
    private val cache=LruCache<String,CachedIcon>(128)
    private val locks=Array(16){Mutex()}
    @Volatile private var clearing=false
    @Volatile private var closed=false
    private val attempts=LruCache<String,Long>(256)
    private val permits=Semaphore(3)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).followRedirects(false).build()
    var revision by mutableIntStateOf(0);private set
    init{scope.launch{disk.withLock{IconCachePolicy.trim(directory)}}}
    private fun origin(url:String)=url.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()
    private fun file(key:String)=File(directory,MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}+".png")
    private fun cached(key:String):Bitmap? {
        val entry=cache.get(key)?:return null
        if(IconCachePolicy.fresh(entry.savedAt,System.currentTimeMillis()))return entry.bitmap
        cache.remove(key);return null
    }
    fun peek(url:String)=if(clearing||closed)null else origin(url)?.let{cached(it)}
    private fun write(key:String,bitmap:Bitmap,now:Long) {
        directory.mkdirs()
        val path=file(key);val temporary=File(directory,path.name+".tmp")
        try{
            temporary.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            check(temporary.renameTo(path));path.setLastModified(now)
        }finally{temporary.delete()}
        IconCachePolicy.trim(directory,now)
    }
    fun remember(url:String,icon:Bitmap){
        if(clearing||closed)return
        val expected=generation.get()
        val key=origin(url)?:return
        val size=maxOf(icon.width,icon.height);if(size<=0||icon.isRecycled)return
        val factor=minOf(1f,128f/size)
        val bitmap=Bitmap.createScaledBitmap(icon,maxOf(1,(icon.width*factor).toInt()),maxOf(1,(icon.height*factor).toInt()),true).copy(Bitmap.Config.ARGB_8888,false)?:return
        val now=System.currentTimeMillis()
        cache.put(key,CachedIcon(bitmap,now));revision++
        scope.launch{runCatching{disk.withLock{if(expected==generation.get()&&!clearing&&!closed)write(key,bitmap,now)}}}
    }
    suspend fun load(url:String):Bitmap?=withContext(Dispatchers.IO){
        if(clearing||closed)return@withContext null
        val expected=generation.get()
        val key=origin(url)?:return@withContext null
        locks[(key.hashCode() and Int.MAX_VALUE)%locks.size].withLock lookup@{
            if(clearing||closed||expected!=generation.get())return@lookup null
            cached(key)?.let{return@lookup it}
            val path=file(key)
            val stored=disk.withLock {
                if(clearing||closed||expected!=generation.get())return@withLock null
                if(path.exists()&&IconCachePolicy.fresh(path.lastModified(),System.currentTimeMillis())){
                    val bitmap=runCatching{path.inputStream().use{decode(it.readBounded(524289))}}.getOrNull()
                    if(bitmap!=null){cache.put(key,CachedIcon(bitmap,path.lastModified()));return@withLock bitmap}
                }
                path.delete();null
            }
            if(stored!=null)return@lookup stored
            val now=System.currentTimeMillis()
            val attempted=attempts.get(key)?:0
            if(now>=attempted&&now-attempted<86_400_000)return@lookup null
            attempts.put(key,now)
            val icon=runCatching{permits.withPermit{
                val target=key.toHttpUrlOrNull()!!.newBuilder().encodedPath("/favicon.ico").build()
                client.newCall(Request.Builder().url(target).build()).execute().use{r->
                    if(!r.isSuccessful)return@use null
                    val body=r.body?:return@use null
                    if(body.contentLength()>524288)return@use null
                    val bytes=body.byteStream().readBounded(524289);if(bytes.size>524288)return@use null
                    decode(bytes)
                }
            }}.getOrNull()
            disk.withLock {
                if(clearing||closed||expected!=generation.get())return@withLock null
                if(icon!=null){cache.put(key,CachedIcon(icon,now));runCatching{write(key,icon,now)}}
                icon
            }
        }
    }
    /** Wait for disk deletion and invalidate in-flight fetches/writes before reporting success. */
    internal suspend fun clear() {
        clearing=true;generation.incrementAndGet();client.dispatcher.cancelAll()
        try{
            withContext(Dispatchers.IO){disk.withLock{
                cache.evictAll();attempts.evictAll()
                directory.listFiles()?.filter{it.isFile}?.forEach{check(it.delete()||!it.exists()){"網站圖示快取未能刪除"}}
            }}
        }finally{clearing=false;withContext(Dispatchers.Main.immediate){revision++}}
    }
    fun close(){closed=true;scope.cancel();client.dispatcher.cancelAll()}
    companion object{
        // Activity recreation must not let an old writer race a new controller's deletion.
        private val disk=Mutex()
        private val generation=AtomicLong(0)
        internal fun decode(bytes:ByteArray):Bitmap?{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth !in 1..4096||bounds.outHeight !in 1..4096)return null
            val options=BitmapFactory.Options().apply{inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/128)}
            return BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        }
    }
}

@Composable internal fun SiteIcon(c:BrowserController,url:String,size:Dp=36.dp){
    val revision=c.icons.revision
    val bitmap by produceState(c.icons.peek(url),url,revision){value=c.icons.load(url)}
    Surface(modifier=Modifier.size(size).testTag("site-icon"),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceVariant){
        Box(contentAlignment=Alignment.Center){
            bitmap?.takeUnless{it.isRecycled}?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize().padding(6.dp),contentScale=ContentScale.Fit)}
                ?:Text(Domains.scope(url).firstOrNull()?.uppercaseChar()?.toString()?:"•",fontSize=16.sp,color=MaterialTheme.colorScheme.primary)
        }
    }
}
