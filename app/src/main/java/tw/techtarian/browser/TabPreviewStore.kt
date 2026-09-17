package tw.techtarian.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Regular-tab thumbnails are session data, not an evictable WebView/cache entry. */
internal class TabPreviewStore(context:Context) {
    internal val directory=File(context.noBackupFilesDir,"tab-previews-v1")
    private val main=Handler(Looper.getMainLooper())
    companion object {
        private val keyPattern=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        // Shared across Activity instances: an old queued save cannot race a new deletion/read.
        private val io=Executors.newSingleThreadExecutor{task->Thread(task,"tab-previews").apply{isDaemon=true}}
        fun newKey():String=UUID.randomUUID().toString()
        fun validKey(key:String?)=key!=null&&keyPattern.matches(key)
    }
    private fun file(key:String)=AtomicFile(File(directory,"$key.png"))
    fun save(key:String?,bitmap:Bitmap,onFailure:()->Unit={}) {
        if(!validKey(key))return // Incognito tabs never have a disk key.
        io.execute {
            val success=runCatching {
                require(!bitmap.isRecycled&&bitmap.width in 1..300&&bitmap.height in 1..400)
                val bytes=ByteArrayOutputStream().use{out->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()
                }
                require(bytes.size<=1_000_000)
                check(directory.isDirectory||directory.mkdirs())
                val target=file(key!!)
                val output=target.startWrite()
                try{output.write(bytes);target.finishWrite(output)}
                catch(error:Exception){target.failWrite(output);throw error}
            }.isSuccess
            if(!success)main.post{onFailure()}
        }
    }
    fun load(key:String?,onLoaded:(Bitmap?)->Unit) {
        if(!validKey(key)){onLoaded(null);return}
        io.execute {
            val bitmap=runCatching {
                val bytes=file(key!!).openRead().use{it.readBounded(1_000_001)}
                require(bytes.size<=1_000_000)
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth in 1..300&&bounds.outHeight in 1..400)
                BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            }.getOrNull()
            main.post{onLoaded(bitmap)}
        }
    }
    fun remove(key:String?) {
        if(validKey(key))io.execute{file(key!!).delete()}
    }
    /** Called only after the full regular-tab manifest has been restored and saved. */
    fun retainOnly(keys:Set<String>) {
        io.execute {
            directory.listFiles()?.forEach { entry->
                val key=entry.name.substringBefore('.')
                if(validKey(key)&&key !in keys)file(key).delete()
            }
        }
    }
    /** Bounded lifecycle drain. Compression and normal reads/writes run on the I/O worker. */
    fun flush():Boolean=runCatching{io.submit{}.get(1500,TimeUnit.MILLISECONDS);true}.getOrDefault(false)
}

/** A stable identity per tab, never per URL or transient list position. */
internal data class SavedBrowserTab(val key:String,val url:String,val title:String,val favoriteId:String?)
