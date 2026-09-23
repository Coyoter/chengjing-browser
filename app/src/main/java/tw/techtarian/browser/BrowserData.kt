package tw.techtarian.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Domains {
    fun scope(url: String): String = url.toHttpUrlOrNull()?.let { it.topPrivateDomain() ?: it.host }.orEmpty()
    fun matches(host: String, scope: String) = scope.isNotEmpty() && (host == scope || host.endsWith(".$scope"))
    fun resolve(input:String,searchSettings:SearchSettings=SearchSettings()):AddressResolution {
        val text = input.trim()
        if (text.isEmpty()) return AddressResolution("",false)
        if (text.startsWith("https://") || text.startsWith("http://")) return AddressResolution(if(text.toHttpUrlOrNull()!=null)text else "",false)
        if (!text.contains(' ') && (text.contains('.') || text.startsWith("localhost"))) {
            val scheme = if (text.startsWith("localhost") || text.startsWith("127.0.0.1") || text.startsWith("10.0.2.2")) "http://" else "https://"
            return AddressResolution((scheme+text).toHttpUrlOrNull()?.toString().orEmpty(),false)
        }
        return AddressResolution(SearchEngines.searchUrl(text,searchSettings),true)
    }
    fun address(input:String,searchSettings:SearchSettings=SearchSettings())=resolve(input,searchSettings).url
}

data class ElementRule(val selector: String, val label: String = "網站元件") {
    fun json() = JSONObject().put("selector", selector).put("label", label)
}
data class SiteRules(
    val domain: String,
    val rules: List<ElementRule> = emptyList(),
    val css: String = "",
    val js: String = "",
    val unlockScroll: Boolean = false,
    val guard: Boolean = false,
    val html:String = "",
    val edits:List<PageEdit> = emptyList(),
) {
    fun json() = JSONObject().put("domain", domain).put("rules", JSONArray(rules.map { it.json() }))
        .put("css", css).put("js", js).put("unlockScroll", unlockScroll).put("guard", guard).put("html",html).put("edits",JSONArray(edits.map{it.json()}))
    companion object {
        fun from(j: JSONObject): SiteRules {
            val a = j.optJSONArray("rules") ?: JSONArray()
            return SiteRules(j.getString("domain"), (0 until a.length()).map {
                val r = a.getJSONObject(it); ElementRule(r.getString("selector"), r.optString("label", "網站元件"))
            }, j.optString("css"), j.optString("js"), j.optBoolean("unlockScroll"), j.optBoolean("guard"), j.optString("html"),j.optJSONArray("edits")?.let{e->(0 until e.length()).map{PageEdit.from(e.getJSONObject(it))}}?:emptyList())
        }
    }
}

object RuleValidation {
    fun selectorError(selector: String): String? {
        if (selector.isBlank() || selector.length > 1200) return "元件規則為空白或太長"
        if (selector.contains(Regex("[{};\\n\\r]"))) return "請輸入元件選擇器，不是整段樣式"
        if (selector.split(',').any { it.trim().lowercase() in setOf("*", "html", "body", ":root", "html > body", "html body") }) return "不能移除整個頁面，請縮小選取範圍"
        return null
    }
    fun parseAi(raw: String, current: SiteRules): AiProposal {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val j = JSONObject(clean)
        val add = j.optJSONArray("add") ?: JSONArray()
        require(add.length() <= 12) { "AI 建議一次修改太多元件，請描述更小的範圍" }
        val additions = (0 until add.length()).map { i ->
            val s = add.getString(i); require(selectorError(s) == null) { selectorError(s).orEmpty() }; ElementRule(s, "AI 調整")
        }
        val remove = j.optJSONArray("remove") ?: JSONArray()
        val removed = (0 until remove.length()).map { remove.getString(it) }
        require(removed.all { s -> current.rules.any { it.selector == s } }) { "AI 嘗試修改不存在的規則" }
        val result = current.copy(rules = (current.rules.filterNot { it.selector in removed } + additions).distinctBy { it.selector }, unlockScroll = j.optBoolean("unlockScroll", current.unlockScroll))
        require(result.rules.size <= 100) { "單一網域最多 100 條規則" }
        return AiProposal(j.optString("explanation", "調整元件規則").take(2000), result, additions.map { it.selector }, removed)
    }
}
data class AiProposal(val explanation: String, val result: SiteRules, val added: List<String>, val removed: List<String>)

class BrowserStore(context: Context) {
    var onSiteChange:(()->Unit)?=null
    var syncSiteSettings:Boolean
        get()=prefs.getBoolean("sync-site-settings",false)
        set(value){check(prefs.edit().putBoolean("sync-site-settings",value).commit())}

    private val prefs = context.getSharedPreferences("browser-v1", Context.MODE_PRIVATE)
    fun certificateException(url:String):Boolean = CertificateExceptions.site(url)?.let{site->prefs.getStringSet("certificate-exceptions",emptySet()).orEmpty().any{CertificateExceptions.site(it)==site}}?:false
    fun setCertificateException(url:String,enabled:Boolean){
        val origin=requireNotNull(CertificateExceptions.site(url)){"只有 HTTPS 網站可設定憑證例外"}
        val values=prefs.getStringSet("certificate-exceptions",emptySet()).orEmpty().toMutableSet()
        values.removeAll{CertificateExceptions.site(it)==origin}
        if(enabled)values.add(origin)
        check(prefs.edit().putStringSet("certificate-exceptions",values).commit()){"設定儲存失敗"}
    }
    // SharedPreferences alone cannot invalidate the parent settings composition.
    // Both the provider selector and its settings fields observe this single state.
    private var providerState by mutableStateOf(
        prefs.getString("ai-provider",null)?.takeIf{it in setOf("gemma","openrouter")}
            ?: if(hasKey())"openrouter"else"gemma"
    )
    var aiProvider:String
        get()=providerState
        set(value){
            require(value in setOf("gemma","openrouter"))
            prefs.edit().putString("ai-provider",value).apply()
            providerState=value
        }
    private var themeState by mutableStateOf(prefs.getString("theme","system")?.takeIf{it in setOf("light","dark","system")}?:"system")
    var theme:String
        get()=themeState
        set(value){require(value in setOf("light","dark","system"));prefs.edit().putString("theme",value).apply();themeState=value}
    var model: String
        get() = prefs.getString("model", "deepseek/deepseek-v4.1-flash") ?: "deepseek/deepseek-v4.1-flash"
        set(value) { prefs.edit().putString("model", value.trim()).apply() }
    var userAgentMode:String
        get()=prefs.getString("user-agent-mode","chrome")?:"chrome"
        set(value){prefs.edit().putString("user-agent-mode",value).apply()}
    var customUserAgent:String
        get()=prefs.getString("custom-user-agent","").orEmpty()
        set(value){prefs.edit().putString("custom-user-agent",value).apply()}
    var addressAtBottom:Boolean
        get()=prefs.getBoolean("address-at-bottom",false)
        set(value){prefs.edit().putBoolean("address-at-bottom",value).apply()}
    private var searchEngineState by mutableStateOf(prefs.getString("search-engine","google")?.takeIf{it in SearchEngines.ids}?:"google")
    private var customSearchTemplateState by mutableStateOf(prefs.getString("custom-search-template","").orEmpty().takeIf{SearchEngines.templateError(it)==null}.orEmpty())
    val searchSettings get()=SearchSettings(searchEngine,customSearchTemplateState)
    val searchEngine get()=if(searchEngineState=="custom"&&customSearchTemplateState.isEmpty())"google"else searchEngineState
    val customSearchTemplate get()=customSearchTemplateState
    fun useSearchEngine(id:String){
        require(id in SearchEngines.ids&&id!="custom")
        check(prefs.edit().putString("search-engine",id).commit()){ "搜尋引擎設定未能儲存" }
        searchEngineState=id
    }
    fun useCustomSearch(raw:String){
        val template=SearchEngines.normalizeTemplate(raw)
        check(prefs.edit().putString("custom-search-template",template).putString("search-engine","custom").commit()){ "自訂搜尋引擎未能儲存" }
        customSearchTemplateState=template;searchEngineState="custom"
    }
    fun all(): List<SiteRules> = prefs.all.keys.filter { it.startsWith("site:") }.mapNotNull { key ->
        runCatching { SiteRules.from(JSONObject(prefs.getString(key, "{}")!!)) }.getOrNull()
    }
    fun get(domain: String) = all().find { it.domain == domain } ?: SiteRules(domain)
    fun save(site: SiteRules) {
        require(site.domain.isNotEmpty())
        require(site.rules.all { RuleValidation.selectorError(it.selector) == null })
        require(site.css.length <= 50000 && site.js.length <= 50000 && site.html.length<=64000 && site.rules.size <= 100 && site.edits.size<=100)
        site.edits.forEach{it.validate()}
        check(prefs.edit().putString("previous:${site.domain}", get(site.domain).json().toString()).putString("site:${site.domain}", site.json().toString()).putLong("site-clock:${site.domain}",maxOf(System.currentTimeMillis(),prefs.getLong("site-clock:${site.domain}",0)+1)).commit()) { "手機儲存失敗，規則尚未儲存" }
        onSiteChange?.invoke()
    }
    fun hasPrevious(domain: String) = prefs.contains("previous:$domain")
    fun undo(domain: String): Boolean {
        val previous = prefs.getString("previous:$domain", null) ?: return false
        check(prefs.edit().putString("site:$domain", previous).remove("previous:$domain").putLong("site-clock:$domain",maxOf(System.currentTimeMillis(),prefs.getLong("site-clock:$domain",0)+1)).commit())
        onSiteChange?.invoke()
        return true
    }
    fun siteRecords():List<SiteSettingsRecord> = all().map{SiteSettingsRecord(it,prefs.getLong("site-clock:${it.domain}",1))}
    fun mergeSiteRecords(remote:List<SiteSettingsRecord>):Set<String>{
        val before=siteRecords().associateBy{it.settings.domain};val merged=SiteSettingsFormat.merge(before.values.toList(),remote)
        val edit=prefs.edit();val changed=mutableSetOf<String>()
        merged.forEach{record->
            val d=record.settings.domain;val old=before[d]
            if(old!=record){
                if(old?.settings!=record.settings){edit.putString("previous:$d",(old?.settings?:SiteRules(d)).json().toString());changed.add(d)}
                edit.putString("site:$d",record.settings.json().toString()).putLong("site-clock:$d",record.modified)
            }
        }
        check(edit.commit()){ "網站設定合併未完成，本機資料已保留" }
        return changed
    }
    val bookmarkStore = BookmarkStore(context)
    fun bookmark(url: String, title: String) { bookmarkStore.toggle(url, title) }
    fun bookmarks(): List<Pair<String,String>> = bookmarkStore.visible().map { it.url to it.title }
    internal fun historyPages()=BrowsingHistoryFormat.pages(prefs.getString("history","[]")?:"[]")
    internal fun historySearches()=BrowsingHistoryFormat.searches(prefs.getString("searches","[]")?:"[]")
    fun history(): List<Pair<String,String>> = historyPages().map{it.url to it.title}
    fun visit(url: String, title: String,visitedAt:Long=System.currentTimeMillis()) {
        if (!url.startsWith("https://")&&!url.startsWith("http://")) return
        val items = (listOf(HistoryPage(url,title,visitedAt)) + historyPages().filterNot { it.url == url }).take(250)
        prefs.edit().putString("history",BrowsingHistoryFormat.pagesJson(items)).apply()
    }
    fun searches():List<String> = historySearches().map{it.query}
    fun recordSearch(input:String,resolved:String,searchedAt:Long=System.currentTimeMillis(),isSearch:Boolean=SearchEngines.looksLikeSearch(resolved)){
        val query=input.trim()
        if(query.isEmpty()||query.startsWith("http://",true)||query.startsWith("https://",true)||!isSearch)return
        val rows=(listOf(HistorySearch(query,searchedAt))+historySearches().filterNot{it.query.equals(query,true)}).take(100)
        prefs.edit().putString("searches",BrowsingHistoryFormat.searchesJson(rows)).apply()
    }
    fun removeHistory(url:String) {
        val rows=historyPages().filterNot{it.url==url}
        prefs.edit().putString("history",BrowsingHistoryFormat.pagesJson(rows)).apply()
    }
    fun clearHistory() { prefs.edit().remove("history").remove("searches").apply() }
    internal fun clearHistory(window:BrowsingWindow) {
        check(prefs.edit()
            .putString("history",BrowsingHistoryFormat.pagesJson(historyPages().filterNot{window.contains(it.visitedAt)}))
            .putString("searches",BrowsingHistoryFormat.searchesJson(historySearches().filterNot{window.contains(it.searchedAt)}))
            .commit()){"瀏覽記錄未能儲存，請檢查手機儲存空間"}
    }
    fun saveTabs(urls:List<String>,favoriteIds:List<String?> = emptyList(),previewKeys:List<String> = emptyList(),titles:List<String> = emptyList(),lastActiveTimes:List<Long> = emptyList(),openedExternally:List<Boolean> = emptyList()):Boolean {
        val links=JSONArray(urls.mapIndexed{i,url->JSONObject().put("url",url).put("id",favoriteIds.getOrNull(i)?:JSONObject.NULL)
            .put("previewKey",previewKeys.getOrNull(i)?:TabPreviewStore.newKey()).put("title",titles.getOrNull(i)?:url)
            .put("lastActiveAt",lastActiveTimes.getOrNull(i)?:0).put("openedExternally",openedExternally.getOrNull(i)?:false)})
        // Keep the legacy keys in the same transaction: existing bookmarks and migrations still work.
        return prefs.edit().putString("tabs",JSONArray(urls).toString()).putString("tab-favorite-links",links.toString()).commit()
    }
    internal fun savedTabRecords():List<SavedBrowserTab> {
        val links=runCatching{JSONArray(prefs.getString("tab-favorite-links","[]"))}.getOrDefault(JSONArray())
        val used=mutableSetOf<String>()
        return tabs().mapIndexed{i,url->
            val row=links.optJSONObject(i)?.takeIf{it.optString("url")==url}
            val existing=row?.optString("previewKey")
            val key=existing?.takeIf{TabPreviewStore.validKey(it)&&used.add(it)}?:TabPreviewStore.newKey().also{used.add(it)}
            SavedBrowserTab(key,url,row?.optString("title")?.takeIf{it.isNotBlank()}?.take(180)?:url,
                row?.takeUnless{it.isNull("id")}?.optString("id")?.takeIf{it.isNotBlank()},row?.optLong("lastActiveAt",0)?.coerceAtLeast(0)?:0,row?.optBoolean("openedExternally",false)?:false)
        }
    }
    fun tabs(): List<String> = runCatching { val a = JSONArray(prefs.getString("tabs", "[]")); (0 until a.length()).map { a.getString(it) }.filter { it.isEmpty() || it.toHttpUrlOrNull() != null }.take(20) }.getOrDefault(emptyList())
    fun tabFavoriteLinks():List<Pair<String,String?>> = runCatching{
        val rows=JSONArray(prefs.getString("tab-favorite-links","[]"))
        (0 until rows.length()).map{val j=rows.getJSONObject(it);j.optString("url") to if(j.isNull("id"))null else j.optString("id").takeIf{it.isNotEmpty()}}
    }.getOrDefault(emptyList())

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("chengjing-browser-openrouter", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("chengjing-browser-openrouter", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun hasKey() = prefs.contains("api-key")
    fun saveKey(value: String) {
        if (value.isBlank()) { prefs.edit().remove("api-key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(value.trim().toByteArray())
        check(prefs.edit().putString("api-key", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    fun readKey(): String {
        val encoded = prefs.getString("api-key", null) ?: return ""
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0,12))) }
        return String(cipher.doFinal(data.copyOfRange(12, data.size)))
    }
}
