package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Small, Android-independent rules shared by the toolbar and native context menu. */
internal object PageActionPolicy {
    fun shareUrl(raw:String?):String? {
        if(raw.isNullOrBlank()||raw.any{it=='\r'||it=='\n'||it=='\u0000'})return null
        val url=raw.toHttpUrlOrNull()?:return null
        // Never send embedded HTTP basic-auth credentials to another application.
        return url.newBuilder().username("").password("").build().toString()
    }
    fun downloadUrl(raw:String?):String? {
        if(raw.isNullOrBlank()||raw.length>32768||raw.any{it=='\r'||it=='\n'||it=='\u0000'})return null
        val url=raw.toHttpUrlOrNull()?:return null
        if(url.username.isNotEmpty()||url.password.isNotEmpty())return null
        return url.newBuilder().fragment(null).build().toString()
    }
    fun resolve(raw:String?,page:String):String? {
        if(raw.isNullOrBlank())return null
        return page.toHttpUrlOrNull()?.resolve(raw)?.toString()?:raw.takeIf{it.contains(':')}
    }
    fun referrer(page:String,target:String):String? {
        val source=page.toHttpUrlOrNull()?:return null
        val destination=target.toHttpUrlOrNull()?:return null
        if(source.isHttps&&!destination.isHttps)return null
        // Only send the origin, never a private page path, query, fragment or password.
        return source.newBuilder().username("").password("").encodedPath("/").query(null).fragment(null).build().toString()
    }
    fun safeFilename(guessed:String,suffix:String):String {
        var clean=guessed.map{if(it.code<32||it.code==127||it in "/\\:*?\"<>|")'_'else it}.joinToString("").trim(' ','.')
        if(clean.isBlank())clean="image"
        // Keep a conservative UTF-8 byte budget, including the unique suffix/extension.
        val dot=clean.lastIndexOf('.')
        val extension=if(dot>0&&clean.length-dot<=12)clean.substring(dot).takeIf{it.drop(1).all{c->c.isLetterOrDigit()}}.orEmpty()else""
        var stem=if(extension.isNotEmpty())clean.dropLast(extension.length)else clean
        while(stem.toByteArray(Charsets.UTF_8).size>160)stem=stem.dropLast(1)
        return "${stem.ifBlank{"image"}}-$suffix$extension"
    }
}
