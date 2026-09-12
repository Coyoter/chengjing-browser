package tw.techtarian.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
    fun address(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("https://") || text.startsWith("http://")) return if (text.toHttpUrlOrNull() != null) text else ""
        if (!text.contains(' ') && (text.contains('.') || text.startsWith("localhost"))) {
            val scheme = if (text.startsWith("localhost") || text.startsWith("127.0.0.1") || text.startsWith("10.0.2.2")) "http://" else "https://"
            return (scheme + text).toHttpUrlOrNull()?.toString().orEmpty()
        }
        return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
    }
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
) {
    fun json() = JSONObject().put("domain", domain).put("rules", JSONArray(rules.map { it.json() }))
        .put("css", css).put("js", js).put("unlockScroll", unlockScroll).put("guard", guard)
    companion object {
        fun from(j: JSONObject): SiteRules {
            val a = j.optJSONArray("rules") ?: JSONArray()
            return SiteRules(j.getString("domain"), (0 until a.length()).map {
                val r = a.getJSONObject(it); ElementRule(r.getString("selector"), r.optString("label", "網站元件"))
            }, j.optString("css"), j.optString("js"), j.optBoolean("unlockScroll"), j.optBoolean("guard"))
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
    private val prefs = context.getSharedPreferences("browser-v1", Context.MODE_PRIVATE)
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) { prefs.edit().putString("theme", value).apply() }
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
    fun all(): List<SiteRules> = prefs.all.keys.filter { it.startsWith("site:") }.mapNotNull { key ->
        runCatching { SiteRules.from(JSONObject(prefs.getString(key, "{}")!!)) }.getOrNull()
    }
    fun get(domain: String) = all().find { it.domain == domain } ?: SiteRules(domain)
    fun save(site: SiteRules) {
        require(site.domain.isNotEmpty())
        require(site.rules.all { RuleValidation.selectorError(it.selector) == null })
        require(site.css.length <= 50000 && site.js.length <= 50000 && site.rules.size <= 100)
        check(prefs.edit().putString("previous:${site.domain}", get(site.domain).json().toString()).putString("site:${site.domain}", site.json().toString()).commit()) { "手機儲存失敗，規則尚未儲存" }
    }
    fun hasPrevious(domain: String) = prefs.contains("previous:$domain")
    fun undo(domain: String): Boolean {
        val previous = prefs.getString("previous:$domain", null) ?: return false
        check(prefs.edit().putString("site:$domain", previous).remove("previous:$domain").commit())
        return true
    }
    val bookmarkStore = BookmarkStore(context)
    fun bookmark(url: String, title: String) { bookmarkStore.toggle(url, title) }
    fun bookmarks(): List<Pair<String,String>> = bookmarkStore.visible().map { it.url to it.title }
    fun history(): List<Pair<String,String>> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]")); (0 until a.length()).map { a.getJSONObject(it).getString("url") to a.getJSONObject(it).getString("title") }
    }.getOrDefault(emptyList())
    fun visit(url: String, title: String) {
        if (!url.startsWith("http")) return
        val items = (listOf(url to title) + history().filterNot { it.first == url }).take(250)
        prefs.edit().putString("history", JSONArray(items.map { JSONObject().put("url", it.first).put("title", it.second) }).toString()).apply()
    }
    fun clearHistory() { prefs.edit().remove("history").apply() }
    fun saveTabs(urls: List<String>,favoriteIds:List<String?> = emptyList()) {
        val links=JSONArray(urls.mapIndexed{i,url->JSONObject().put("url",url).put("id",favoriteIds.getOrNull(i)?:JSONObject.NULL)})
        prefs.edit().putString("tabs",JSONArray(urls).toString()).putString("tab-favorite-links",links.toString()).apply()
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
