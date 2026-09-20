package tw.techtarian.browser

import java.io.File

/** Only app-owned favicon files; never WebView databases, downloads, or tab previews. */
internal object IconCachePolicy {
    const val MAX_AGE_MILLIS=30*24*60*60_000L
    const val MAX_BYTES=8*1024*1024L
    const val MAX_ENTRIES=256
    fun fresh(savedAt:Long,now:Long)=savedAt>0&&savedAt<=now&&now-savedAt<MAX_AGE_MILLIS

    fun trim(directory:File,now:Long=System.currentTimeMillis()) {
        val files=directory.listFiles()?.filter{it.isFile}?:return
        files.filter{it.extension=="tmp"||!fresh(it.lastModified(),now)}.forEach{it.delete()}
        val remaining=files.filter{it.exists()}.sortedBy{it.lastModified()}
        var bytes=remaining.sumOf{it.length()}
        var count=remaining.size
        for(file in remaining) {
            if(bytes<=MAX_BYTES&&count<=MAX_ENTRIES)break
            val length=file.length()
            if(file.delete()){bytes-=length;count--}
        }
    }
}
