package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Site choices follow WebView's host-based TLS decision cache, across ports. */
object CertificateExceptions {
    fun site(url:String):String? = url.toHttpUrlOrNull()?.takeIf{it.isHttps}?.newBuilder()?.username("")?.password("")?.port(443)?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()
    fun sameDocument(a:String,b:String):Boolean{
        val left=a.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?:return false
        return left==b.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()
    }
}
