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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SiteIcons(context:Context){
    private val directory=File(context.cacheDir,"site-icons").apply{mkdirs()}
    private val cache=LruCache<String,Bitmap>(128)
    private val locks=ConcurrentHashMap<String,Mutex>()
    private val attempts=ConcurrentHashMap<String,Long>()
    private val permits=Semaphore(3)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).followRedirects(false).build()
    var revision by mutableIntStateOf(0);private set
    private fun origin(url:String)=url.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()
    private fun file(key:String)=File(directory,MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}+".png")
    fun peek(url:String)=origin(url)?.let{cache.get(it)}
    fun remember(url:String,icon:Bitmap){
        val key=origin(url)?:return
        val size=maxOf(icon.width,icon.height);if(size<=0||icon.isRecycled)return
        val factor=minOf(1f,128f/size)
        val bitmap=Bitmap.createScaledBitmap(icon,maxOf(1,(icon.width*factor).toInt()),maxOf(1,(icon.height*factor).toInt()),true).copy(Bitmap.Config.ARGB_8888,false)?:return
        cache.put(key,bitmap);revision++
        scope.launch{runCatching{locks.getOrPut(key){Mutex()}.withLock{file(key).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}}}
    }
    suspend fun load(url:String):Bitmap?=withContext(Dispatchers.IO){
        val key=origin(url)?:return@withContext null
        locks.getOrPut(key){Mutex()}.withLock{
            cache.get(key)?.let{return@withLock it}
            val path=file(key)
            if(path.exists())BitmapFactory.decodeFile(path.path)?.let{cache.put(key,it);return@withLock it}
            if(System.currentTimeMillis()-(attempts[key]?:0)<86_400_000)return@withLock null
            attempts[key]=System.currentTimeMillis()
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
            if(icon!=null){cache.put(key,icon);runCatching{path.outputStream().use{icon.compress(Bitmap.CompressFormat.PNG,100,it)}}}
            icon
        }
    }
    fun close(){scope.cancel();client.dispatcher.cancelAll()}
    companion object{
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
