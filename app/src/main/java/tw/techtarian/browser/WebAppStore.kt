package tw.techtarian.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

internal enum class PinOutcome { REQUESTED, UPDATED, UNSUPPORTED, FAILED }

internal class WebAppStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("browser-v1", Context.MODE_PRIVATE)
    private val iconsDir = File(appContext.filesDir, "web-app-icons").apply { mkdirs() }
    var revision by mutableIntStateOf(0)
        private set

    fun list(): List<InstalledWebApp> = WebAppPolicy.parseStoredList(prefs.getString(PREFS_KEY, "[]"))

    fun get(id: String): InstalledWebApp? =
        if (!WebAppPolicy.validId(id)) null else list().firstOrNull { it.id == id }

    fun findByUrl(rawUrl: String?): InstalledWebApp? {
        val clean = WebAppPolicy.installableUrl(rawUrl) ?: return null
        val exactId = WebAppPolicy.idForUrl(clean)
        val apps = list()
        return apps.firstOrNull { it.id == exactId || it.url == clean }
    }

    fun save(app: InstalledWebApp): InstalledWebApp {
        val existing = list().filterNot { it.id == app.id }
        val merged = (listOf(app) + existing).take(WebAppPolicy.MAX_INSTALLED)
        check(prefs.edit().putString(PREFS_KEY, WebAppPolicy.serializeList(merged)).commit())
        revision++
        return app
    }

    fun remove(id: String): InstalledWebApp? {
        val current = list()
        val target = current.firstOrNull { it.id == id } ?: return null
        val remaining = current.filterNot { it.id == id }
        check(prefs.edit().putString(PREFS_KEY, WebAppPolicy.serializeList(remaining)).commit())
        target.iconFile?.let { name -> runCatching { File(iconsDir, name).delete() } }
        revision++
        return target
    }

    fun saveIcon(id: String, bitmap: Bitmap): String? {
        if (!WebAppPolicy.validId(id) || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        return runCatching {
            iconsDir.mkdirs()
            val filename = "$id.png"
            val target = File(iconsDir, filename)
            val tmp = File(iconsDir, "$filename.tmp")
            val scaled = scaleIcon(bitmap, 256)
            try {
                tmp.outputStream().use { out ->
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out))
                }
                check(tmp.renameTo(target))
                filename
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                tmp.delete()
            }
        }.getOrNull()
    }

    fun loadIcon(app: InstalledWebApp): Bitmap? {
        val name = app.iconFile ?: return null
        val file = File(iconsDir, name)
        if (!file.isFile) return null
        return runCatching {
            file.inputStream().use { stream ->
                val bytes = stream.readBounded(1_048_577)
                if (bytes.size > 1_048_576) null else decodeBitmap(bytes, 256)
            }
        }.getOrNull()
    }

    companion object {
        private const val PREFS_KEY = "installed-web-apps"
        private const val DEFAULT_ACCENT = 0xFF147A64.toInt()
        private const val LIGHT_CARD = 0xFFFBFAF6.toInt()
        private const val LIGHT_INK = 0xFFFFFDF7.toInt()

        internal fun decodeBitmap(bytes: ByteArray, maxSize: Int = 256): Bitmap? {
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxSize)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

        private fun scaleIcon(source: Bitmap, maxDim: Int): Bitmap {
            val size = maxOf(source.width, source.height)
            if (size <= maxDim) return source
            val factor = maxDim.toFloat() / size
            return Bitmap.createScaledBitmap(
                source,
                maxOf(1, (source.width * factor).toInt()),
                maxOf(1, (source.height * factor).toInt()),
                true,
            )
        }

        private fun isColorTooLight(color: Int): Boolean {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            return lum > 0.72
        }

        /** Render a crisp 192x192 adaptive-icon canvas respecting Android's safe inset zone. */
        internal fun renderAdaptiveBitmap(
            source: Bitmap?,
            name: String,
            url: String,
            themeColor: Int?,
        ): Bitmap {
            val canvasSize = 192
            val out = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val usableSource = source?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            if (usableSource != null) {
                canvas.drawColor(LIGHT_CARD)
                // Android adaptive icons mask up to 18dp on each side of a 108dp canvas;
                // keep the site logo comfortably centered within the 56% safe zone.
                val maxInner = canvasSize * 0.56f
                val ratio = minOf(maxInner / usableSource.width, maxInner / usableSource.height)
                val drawW = usableSource.width * ratio
                val drawH = usableSource.height * ratio
                val left = (canvasSize - drawW) / 2f
                val top = (canvasSize - drawH) / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(usableSource, null, RectF(left, top, left + drawW, top + drawH), paint)
            } else {
                val bg = themeColor?.takeUnless(::isColorTooLight) ?: DEFAULT_ACCENT
                canvas.drawColor(bg)
                val initial = WebAppPolicy.badgeInitial(name, url)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = LIGHT_INK
                    textSize = 72f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                val bounds = Rect()
                paint.getTextBounds(initial, 0, initial.length, bounds)
                val baseline = canvasSize / 2f - (bounds.top + bounds.bottom) / 2f
                canvas.drawText(initial, canvasSize / 2f, baseline, paint)
            }
            return out
        }
    }
}

internal object WebAppLauncher {
    const val ACTION_OPEN_WEB_APP = "tw.techtarian.browser.action.OPEN_WEB_APP"
    const val EXTRA_WEB_APP_ID = "chengjing_webapp_id"

    fun canPin(context: Context): Boolean =
        runCatching { context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true }
            .getOrDefault(false)

    fun launchIntent(context: Context, app: InstalledWebApp): Intent =
        if (app.mode == WebAppDisplayMode.SHORTCUT) {
            Intent(Intent.ACTION_VIEW, Uri.parse(app.url))
                .setClass(context, MainActivity::class.java)
                .putExtra(EXTRA_WEB_APP_ID, app.id)
        } else {
            Intent(ACTION_OPEN_WEB_APP, Uri.parse(WebAppPolicy.shortcutUri(app.id)))
                .setClass(context, WebAppActivity::class.java)
                .putExtra(EXTRA_WEB_APP_ID, app.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

    private fun buildShortcutInfo(context: Context, app: InstalledWebApp, iconBitmap: Bitmap?): ShortcutInfo {
        val adaptive = WebAppStore.renderAdaptiveBitmap(iconBitmap, app.name, app.url, app.themeColor)
        return ShortcutInfo.Builder(context, WebAppPolicy.shortcutId(app.id))
            .setShortLabel(app.name.take(25).ifBlank { app.name })
            .setLongLabel(app.name)
            .setIcon(Icon.createWithAdaptiveBitmap(adaptive))
            .setIntent(launchIntent(context, app))
            .build()
    }

    fun pinToHomeScreen(context: Context, app: InstalledWebApp, iconBitmap: Bitmap?, forcePin: Boolean = false): PinOutcome {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return PinOutcome.UNSUPPORTED
        val info = runCatching { buildShortcutInfo(context, app, iconBitmap) }.getOrNull() ?: return PinOutcome.FAILED
        val shortcutId = WebAppPolicy.shortcutId(app.id)
        val alreadyPinned = runCatching { manager.pinnedShortcuts.any { it.id == shortcutId && it.isEnabled } }.getOrDefault(false)
        if (alreadyPinned && !forcePin) {
            return if (runCatching { manager.updateShortcuts(listOf(info)) }.getOrDefault(false)) {
                PinOutcome.UPDATED
            } else {
                PinOutcome.FAILED
            }
        }
        if (!manager.isRequestPinShortcutSupported) return PinOutcome.UNSUPPORTED
        return if (runCatching { manager.requestPinShortcut(info, null) }.getOrDefault(false)) {
            PinOutcome.REQUESTED
        } else {
            PinOutcome.FAILED
        }
    }

    fun updatePinnedShortcut(context: Context, app: InstalledWebApp, iconBitmap: Bitmap?) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcutId = WebAppPolicy.shortcutId(app.id)
        runCatching {
            if (manager.pinnedShortcuts.any { it.id == shortcutId }) {
                manager.enableShortcuts(listOf(shortcutId))
                manager.updateShortcuts(listOf(buildShortcutInfo(context, app, iconBitmap)))
            }
        }
    }

    fun disablePinnedShortcut(context: Context, appId: String, disabledMessage: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            manager.disableShortcuts(listOf(WebAppPolicy.shortcutId(appId)), disabledMessage)
        }
    }

    fun open(context: Context, app: InstalledWebApp, controller: BrowserController? = null): Boolean {
        if (app.mode == WebAppDisplayMode.SHORTCUT && controller != null) {
            controller.newTab(app.url, incognito = false)
            controller.sheet = ""
            return true
        }
        val intent = launchIntent(context, app)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
