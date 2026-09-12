package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** WebView can cache a proceed decision. Retain its warning for the whole app process too. */
class CertificateWarnings {
    private val resources = mutableMapOf<String, String>()
    private val pages = mutableMapOf<String, LinkedHashSet<String>>()

    @Synchronized fun record(pageUrl: String, resourceUrl: String, reason: String) {
        val resource = origin(resourceUrl)
        val page = origin(pageUrl).ifEmpty { resource }
        val host = resourceUrl.toHttpUrlOrNull()?.host ?: "網站資源"
        val detail = "$host：$reason"
        val resourceHost=tlsHost(resourceUrl)
        if (resourceHost.isNotEmpty()) resources[resourceHost] = detail
        if (page.isNotEmpty()) pages.getOrPut(page) { linkedSetOf() }.add(detail)
    }

    @Synchronized fun carryKnownResource(pageUrl: String, resourceUrl: String) {
        val detail=resources[tlsHost(resourceUrl)]?:return
        val page=origin(pageUrl)
        if(page.isNotEmpty())pages.getOrPut(page){linkedSetOf()}.add(detail)
    }

    @Synchronized fun messageFor(url: String): String {
        val origin = origin(url)
        if (origin.isEmpty()) return ""
        val details = (pages[origin].orEmpty() + listOfNotNull(resources[tlsHost(url)])).distinct()
        if (details.isEmpty()) return ""
        return "此網站本次瀏覽遇到憑證異常，已依設定繼續載入，無法確認連線對象身分。\n" + details.take(5).joinToString("\n")
    }

    private fun origin(url: String): String = url.toHttpUrlOrNull()?.newBuilder()
        ?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString().orEmpty()
    private fun tlsHost(url: String): String = url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host.orEmpty()

    companion object { val session = CertificateWarnings() }
}
