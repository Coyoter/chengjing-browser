package tw.techtarian.browser

import java.util.Locale

internal object DownloadFormat {
    const val MAX_PAGE_BYTES=2L*1024*1024*1024
    private val extensions=mapOf("video/mp4" to "mp4","video/webm" to "webm","video/quicktime" to "mov","video/x-matroska" to "mkv","video/ogg" to "ogv","audio/mpeg" to "mp3","audio/mp4" to "m4a","audio/aac" to "aac","audio/wav" to "wav","audio/x-wav" to "wav","audio/ogg" to "ogg","audio/flac" to "flac","application/pdf" to "pdf","application/zip" to "zip","application/gzip" to "gz","application/x-7z-compressed" to "7z","application/json" to "json","text/csv" to "csv","text/plain" to "txt","text/html" to "html","image/png" to "png","image/jpeg" to "jpg","image/webp" to "webp","image/gif" to "gif","image/svg+xml" to "svg","image/avif" to "avif","image/heif" to "heif","application/epub+zip" to "epub","application/vnd.android.package-archive" to "apk","application/msword" to "doc","application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx","application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx","application/octet-stream" to "bin")
    fun mime(value:String?)=value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf{it.matches(Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))}
    fun inline(url:String)=url.startsWith("blob:")||url.startsWith("data:")
    fun supported(url:String)=inline(url)||PageActionPolicy.downloadUrl(url)!=null
    fun infer(bytes:ByteArray,hint:String?):String {
        fun at(offset:Int,text:String)=bytes.size>=offset+text.length&&text.indices.all{(bytes[offset+it].toInt() and 255)==text[it].code}
        return when {
            at(4,"ftyp")->when{at(8,"avif")||at(8,"avis")->"image/avif";at(8,"heic")||at(8,"mif1")->"image/heif";at(8,"qt  ")->"video/quicktime";at(8,"M4A ")||mime(hint)=="audio/mp4"->"audio/mp4";else->"video/mp4"}
            at(0,"%PDF-")->"application/pdf"
            at(0,"PK\u0003\u0004")->mime(hint)?.takeIf{it.startsWith("application/vnd.")||it=="application/epub+zip"}?:"application/zip"
            at(0,"ID3")->"audio/mpeg"
            at(0,"RIFF")&&at(8,"WAVE")->"audio/wav"
            at(0,"fLaC")->"audio/flac"
            else->mime(hint)?:"application/octet-stream"
        }
    }
    fun filename(suggestion:String?,mime:String):String {
        var name=suggestion.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069<>:\"/\\\\|?*]"),"_").trim().trim('.')
        val extension=extensions[mime]
        if(name.isBlank())name=when{mime.startsWith("video/")->"video";mime.startsWith("audio/")->"audio";else->"download"}
        val old=name.substringAfterLast('.',"").lowercase(Locale.ROOT)
        if(extension!=null&&(old.isEmpty()||old in setOf("bin","dat")))name=name.substringBeforeLast('.',name)+"."+extension
        while(name.toByteArray(Charsets.UTF_8).size>180){
            val dot=name.lastIndexOf('.')
            val end=if(dot>0&&name.length-dot<15)dot else name.length
            name=name.substring(0,name.offsetByCodePoints(end,-1))+name.substring(end)
        }
        return name
    }
}
