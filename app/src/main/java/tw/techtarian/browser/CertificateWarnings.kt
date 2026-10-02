package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Connection problems are recorded without changing trust decisions. */
class CertificateWarnings {
    private val resources = mutableMapOf<String, String>()
    private val issueOrigins = mutableMapOf<String, LinkedHashSet<String>>()
    private val pages = mutableMapOf<String, LinkedHashSet<String>>()

    @Synchronized fun record(pageUrl: String, resourceUrl: String, reason: String) {
        val resource = origin(resourceUrl)
        val page = origin(pageUrl).ifEmpty { resource }
        val host = resourceUrl.toHttpUrlOrNull()?.host ?: bt(R.string.msg_789842abff7d)
        val detail = "$host：$reason"
        val resourceHost=tlsHost(resourceUrl)
        if (resourceHost.isNotEmpty()) resources[resourceHost] = detail
        if (page.isNotEmpty()) {
            pages.getOrPut(page) { linkedSetOf() }.add(detail)
            CertificateExceptions.site(resourceUrl)?.let{issueOrigins.getOrPut(page){linkedSetOf()}.add(it)}
        }
    }

    @Synchronized fun carryKnownResource(pageUrl: String, resourceUrl: String) {
        val detail=resources[tlsHost(resourceUrl)]?:return
        val page=origin(pageUrl)
        if(page.isNotEmpty()){
            pages.getOrPut(page){linkedSetOf()}.add(detail)
            CertificateExceptions.site(resourceUrl)?.let{issueOrigins.getOrPut(page){linkedSetOf()}.add(it)}
        }
    }

    @Synchronized fun originsFor(url:String):List<String> = issueOrigins[origin(url)].orEmpty().toList()

    @Synchronized fun messageFor(url: String): String {
        val origin = origin(url)
        if (origin.isEmpty()) return ""
        val details = (pages[origin].orEmpty() + listOfNotNull(resources[tlsHost(url)])).distinct()
        if (details.isEmpty()) return ""
        return bt(R.string.msg_cb0227da0af8) + details.take(5).joinToString("\n")
    }

    private fun origin(url: String): String = url.toHttpUrlOrNull()?.newBuilder()
        ?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString().orEmpty()
    private fun tlsHost(url: String): String = url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host.orEmpty()

    companion object { val session = CertificateWarnings() }
}
