package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal enum class WebAppDisplayMode(val id: String) {
    STANDALONE("standalone"),
    MINIMAL_UI("minimal_ui"),
    SHORTCUT("shortcut");

    companion object {
        fun fromId(raw: String?): WebAppDisplayMode =
            entries.firstOrNull { it.id == raw } ?: STANDALONE
    }
}

internal data class WebAppMetadata(
    val name: String,
    val url: String,
    val iconUrls: List<String> = emptyList(),
    val themeColor: Int? = null,
    val suggestedMode: WebAppDisplayMode = WebAppDisplayMode.STANDALONE,
)

internal data class InstalledWebApp(
    val id: String,
    val name: String,
    val url: String,
    val scope: String,
    val mode: WebAppDisplayMode = WebAppDisplayMode.STANDALONE,
    val themeColor: Int? = null,
    val iconFile: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun json(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("scope", scope)
        .put("mode", mode.id)
        .put("themeColor", themeColor ?: JSONObject.NULL)
        .put("iconFile", iconFile ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun from(j: JSONObject): InstalledWebApp? {
            val id = j.optString("id").takeIf { WebAppPolicy.validId(it) } ?: return null
            val url = WebAppPolicy.installableUrl(j.optString("url")) ?: return null
            val name = WebAppPolicy.sanitizeName(j.optString("name"), url)
            val scope = j.optString("scope").ifBlank { WebAppPolicy.scopeFor(url) }
            val mode = WebAppDisplayMode.fromId(j.optString("mode"))
            val themeColor = if (j.isNull("themeColor")) null else j.optInt("themeColor")
            val iconFile = if (j.isNull("iconFile")) null else j.optString("iconFile").takeIf {
                it.isNotBlank() && !it.contains('/') && !it.contains('\\') && !it.contains("..")
            }
            val createdAt = j.optLong("createdAt", 0L).coerceAtLeast(0L)
            val updatedAt = j.optLong("updatedAt", createdAt).coerceAtLeast(0L)
            return InstalledWebApp(id, name, url, scope, mode, themeColor, iconFile, createdAt, updatedAt)
        }
    }
}

/** Pure, Android-independent rules for installing and scoping web apps. */
internal object WebAppPolicy {
    const val SCHEME = "chengjing-webapp"
    const val HOST = "app"
    const val MAX_NAME_LENGTH = 60
    const val MAX_INSTALLED = 100
    private val ID_PATTERN = Regex("^[a-z0-9_-]{4,48}$")
    private val HEX_COLOR = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val RGB_COLOR = Regex("^rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*[0-9.]+)?\\s*\\)$", RegexOption.IGNORE_CASE)

    fun installableUrl(raw: String?): String? {
        val clean = PageActionPolicy.shareUrl(raw) ?: return null
        val parsed = clean.toHttpUrlOrNull() ?: return null
        if (parsed.host.equals("practice.chengjing.invalid", ignoreCase = true)) return null
        return clean
    }

    fun scopeFor(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return ""
        return parsed.topPrivateDomain() ?: parsed.host.lowercase(Locale.ROOT)
    }

    fun hostFor(url: String): String = url.toHttpUrlOrNull()?.host.orEmpty()

    fun displayUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val path = parsed.encodedPath.takeIf { it != "/" }.orEmpty()
        return parsed.host + path
    }

    fun sanitizeName(raw: String?, fallbackUrl: String): String {
        val cleaned = raw.orEmpty()
            .map { if (it.code < 32 || it.code == 127) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_LENGTH)
            .trim()
        if (cleaned.isNotEmpty()) return cleaned
        return hostFor(fallbackUrl).ifBlank { "Web App" }.take(MAX_NAME_LENGTH)
    }

    fun validId(raw: String?): Boolean = raw != null && ID_PATTERN.matches(raw)

    fun idForUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = digest.take(8).joinToString("") { "%02x".format(it) }
        return "wa_$hex"
    }

    fun shortcutId(appId: String): String = "webapp-$appId"

    fun shortcutUri(appId: String): String = "$SCHEME://$HOST/$appId"

    fun parseShortcutId(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val prefix = "$SCHEME://$HOST/"
        if (!uri.startsWith(prefix)) return null
        val candidate = uri.removePrefix(prefix).substringBefore('?').substringBefore('#')
        return candidate.takeIf { validId(it) }
    }

    fun isInScope(app: InstalledWebApp, currentUrl: String?): Boolean {
        val target = currentUrl?.toHttpUrlOrNull() ?: return false
        val origin = app.url.toHttpUrlOrNull() ?: return false
        if (origin.isHttps && !target.isHttps) return false
        val targetHost = target.host.lowercase(Locale.ROOT)
        val originHost = origin.host.lowercase(Locale.ROOT)
        if (targetHost == originHost) return true
        return app.scope.isNotBlank() && Domains.matches(targetHost, app.scope)
    }

    fun parseThemeColor(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        HEX_COLOR.matchEntire(value)?.let { match ->
            val hex = match.groupValues[1]
            return when (hex.length) {
                3 -> {
                    val r = hex[0].digitToInt(16) * 17
                    val g = hex[1].digitToInt(16) * 17
                    val b = hex[2].digitToInt(16) * 17
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> (0xFF shl 24) or hex.toInt(16)
                8 -> {
                    // CSS 8-digit hex is #RRGGBBAA; preserve RGB as opaque for system bars/task cards.
                    val rgb = hex.substring(0, 6).toInt(16)
                    (0xFF shl 24) or rgb
                }
                else -> null
            }
        }
        RGB_COLOR.matchEntire(value)?.let { match ->
            val r = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val g = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val b = match.groupValues[3].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return null
    }

    private data class IconCandidate(val url: String, val score: Int)

    private fun parseLargestDimension(sizes: String?): Int {
        if (sizes.isNullOrBlank()) return 0
        var best = 0
        Regex("(\\d{2,4})\\s*[xX×]\\s*(\\d{2,4})").findAll(sizes).forEach { m ->
            val w = m.groupValues[1].toIntOrNull() ?: 0
            val h = m.groupValues[2].toIntOrNull() ?: 0
            val dim = minOf(w, h)
            if (dim > best) best = dim
        }
        return best
    }

    private fun isDecodableIcon(url: String, type: String?): Boolean {
        val cleanType = type?.lowercase(Locale.ROOT).orEmpty()
        if ("svg" in cleanType) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        val path = parsed.encodedPath.lowercase(Locale.ROOT)
        if (path.endsWith(".svg") || path.endsWith(".svgz")) return false
        return true
    }

    private fun scoreIcon(url: String, sizes: String?, type: String?, fromManifest: Boolean, appleTouch: Boolean): Int {
        val dim = parseLargestDimension(sizes)
        val sizeScore = when {
            dim in 192..512 -> 100
            dim in 128..191 -> 85
            dim > 512 -> 75
            dim in 64..127 -> 60
            dim in 32..63 -> 35
            dim > 0 -> 20
            appleTouch -> 70
            else -> 40
        }
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.ROOT).orEmpty()
        val formatScore = when {
            "png" in type.orEmpty().lowercase(Locale.ROOT) || path.endsWith(".png") -> 18
            "webp" in type.orEmpty().lowercase(Locale.ROOT) || path.endsWith(".webp") -> 15
            "jpeg" in type.orEmpty().lowercase(Locale.ROOT) || path.endsWith(".jpg") || path.endsWith(".jpeg") -> 10
            path.endsWith(".ico") -> 4
            else -> 8
        }
        val sourceBonus = if (fromManifest) 14 else if (appleTouch) 10 else 0
        return sizeScore + formatScore + sourceBonus
    }

    fun parsePageMetadata(
        pageUrl: String,
        pageTitle: String,
        pageInfoJson: String? = null,
        manifestJson: String? = null,
        manifestUrl: String? = null,
    ): WebAppMetadata {
        val cleanUrl = installableUrl(pageUrl) ?: pageUrl
        val pageInfo = runCatching { pageInfoJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) }.getOrNull()
        val manifest = runCatching { manifestJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) }.getOrNull()
        val manifestBase = manifestUrl?.takeIf { it.isNotBlank() } ?: cleanUrl

        val manifestShort = manifest?.optString("short_name")?.trim().orEmpty()
        val manifestName = manifest?.optString("name")?.trim().orEmpty()
        val appMeta = pageInfo?.optString("applicationName")?.trim().orEmpty()
        val appleMeta = pageInfo?.optString("appleTitle")?.trim().orEmpty()
        val docTitle = pageInfo?.optString("title")?.trim().orEmpty().ifBlank { pageTitle.trim() }

        val chosenName = listOf(
            manifestShort.takeIf { it.isNotEmpty() },
            manifestName.takeIf { it.isNotEmpty() },
            appleMeta.takeIf { it.isNotEmpty() },
            appMeta.takeIf { it.isNotEmpty() },
            docTitle.takeIf { it.isNotEmpty() },
        ).firstOrNull()
        val name = sanitizeName(chosenName, cleanUrl)

        val themeColor = parseThemeColor(manifest?.optString("theme_color"))
            ?: parseThemeColor(pageInfo?.optString("themeColor"))

        val display = manifest?.optString("display")?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val suggestedMode = when (display) {
            "minimal-ui" -> WebAppDisplayMode.MINIMAL_UI
            else -> WebAppDisplayMode.STANDALONE
        }

        val candidates = mutableListOf<IconCandidate>()
        manifest?.optJSONArray("icons")?.let { icons ->
            for (i in 0 until minOf(icons.length(), 24)) {
                val entry = icons.optJSONObject(i) ?: continue
                val src = entry.optString("src").trim()
                val resolved = PageActionPolicy.resolve(src, manifestBase)?.let(PageActionPolicy::shareUrl) ?: continue
                val type = entry.optString("type")
                if (!isDecodableIcon(resolved, type)) continue
                val sizes = entry.optString("sizes")
                candidates.add(IconCandidate(resolved, scoreIcon(resolved, sizes, type, fromManifest = true, appleTouch = false)))
            }
        }
        pageInfo?.optJSONArray("icons")?.let { icons ->
            for (i in 0 until minOf(icons.length(), 24)) {
                val entry = icons.optJSONObject(i) ?: continue
                val href = entry.optString("href").trim()
                val resolved = PageActionPolicy.resolve(href, cleanUrl)?.let(PageActionPolicy::shareUrl) ?: continue
                val type = entry.optString("type")
                if (!isDecodableIcon(resolved, type)) continue
                val rel = entry.optString("rel").lowercase(Locale.ROOT)
                val sizes = entry.optString("sizes")
                candidates.add(IconCandidate(resolved, scoreIcon(resolved, sizes, type, fromManifest = false, appleTouch = "apple-touch-icon" in rel)))
            }
        }

        val orderedIcons = candidates
            .sortedByDescending { it.score }
            .map { it.url }
            .distinct()
            .take(8)

        return WebAppMetadata(
            name = name,
            url = cleanUrl,
            iconUrls = orderedIcons,
            themeColor = themeColor,
            suggestedMode = suggestedMode,
        )
    }

    fun extractManifestUrl(pageUrl: String, pageInfoJson: String?): String? {
        val pageInfo = runCatching { pageInfoJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) }.getOrNull() ?: return null
        val raw = pageInfo.optString("manifestUrl").trim()
        if (raw.isEmpty()) return null
        return PageActionPolicy.resolve(raw, pageUrl)?.let(PageActionPolicy::shareUrl)
    }

    fun badgeInitial(name: String, url: String): String {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            val first = trimmed.codePointAt(0)
            return String(Character.toChars(first)).uppercase(Locale.ROOT)
        }
        val host = hostFor(url)
        return host.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
    }

    fun parseStoredList(raw: String?): List<InstalledWebApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val seen = mutableSetOf<String>()
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let(InstalledWebApp::from)?.takeIf { seen.add(it.id) }
            }.sortedByDescending { it.updatedAt }.take(MAX_INSTALLED)
        }.getOrDefault(emptyList())
    }

    fun serializeList(apps: List<InstalledWebApp>): String =
        JSONArray(apps.take(MAX_INSTALLED).map { it.json() }).toString()
}
