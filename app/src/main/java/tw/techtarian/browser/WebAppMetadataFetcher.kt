package tw.techtarian.browser

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

internal data class ResolvedWebAppInstall(
    val metadata: WebAppMetadata,
    val icon: Bitmap?,
)

internal object WebAppMetadataFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val EXTRACT_SCRIPT = """(()=>{try{
        const m=(n)=>{const e=document.querySelector('meta[name="'+n+'"]');return e?(e.getAttribute('content')||''):'';};
        const manifestEl=document.querySelector('link[rel="manifest"]');
        const icons=[];
        document.querySelectorAll('link[rel]').forEach(l=>{
            const rel=(l.getAttribute('rel')||'').toLowerCase();
            if(rel.includes('icon')||rel.includes('apple-touch-icon')){
                const href=l.getAttribute('href')||'';
                if(href&&icons.length<24)icons.push({href:href,rel:rel,sizes:l.getAttribute('sizes')||'',type:l.getAttribute('type')||''});
            }
        });
        return JSON.stringify({
            title:document.title||'',
            applicationName:m('application-name'),
            appleTitle:m('apple-mobile-web-app-title'),
            themeColor:m('theme-color'),
            manifestUrl:manifestEl?(manifestEl.getAttribute('href')||''):'',
            icons:icons
        });
    }catch(e){return '{}';}})()"""

    suspend fun fetch(controller: BrowserController, tab: BrowserTab): ResolvedWebAppInstall? {
        val cleanUrl = WebAppPolicy.installableUrl(tab.url) ?: return null
        val fallbackTitle = tab.title
        val rawJs = runCatching {
            if (controller.activeId == tab.id) controller.js(EXTRACT_SCRIPT) else "null"
        }.getOrDefault("null")
        val pageInfoJson = runCatching {
            val decoded = JSONTokener(rawJs).nextValue()
            if (decoded is String) decoded else null
        }.getOrNull()

        val manifestUrl = WebAppPolicy.extractManifestUrl(cleanUrl, pageInfoJson)
        val manifestJson = if (manifestUrl != null) fetchText(manifestUrl, 262_144) else null
        val metadata = WebAppPolicy.parsePageMetadata(
            pageUrl = cleanUrl,
            pageTitle = fallbackTitle,
            pageInfoJson = pageInfoJson,
            manifestJson = manifestJson,
            manifestUrl = manifestUrl,
        )
        val icon = fetchBestIcon(controller, cleanUrl, metadata.iconUrls, tab.incognito)
        return ResolvedWebAppInstall(metadata, icon)
    }

    private suspend fun fetchText(url: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > maxBytes) return@use null
                val bytes = body.byteStream().readBounded(maxBytes + 1)
                if (bytes.size > maxBytes) return@use null
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private suspend fun fetchBestIcon(
        controller: BrowserController,
        pageUrl: String,
        candidates: List<String>,
        incognito: Boolean,
    ): Bitmap? {
        for (candidate in candidates) {
            val bitmap = fetchBitmap(candidate)
            if (bitmap != null) return bitmap
        }
        val cached = controller.icons.peek(pageUrl)
        if (cached != null && !cached.isRecycled) return cached
        return if (incognito) null else controller.icons.load(pageUrl)
    }

    private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if ("svg" in contentType) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > 1_048_576) return@use null
                val bytes = body.byteStream().readBounded(1_048_577)
                if (bytes.size > 1_048_576) return@use null
                WebAppStore.decodeBitmap(bytes, 256)
            }
        }.getOrNull()
    }
}
