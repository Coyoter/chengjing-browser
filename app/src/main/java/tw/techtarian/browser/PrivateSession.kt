package tw.techtarian.browser

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** Never emulate privacy by clearing the normal profile or merely suppressing history. */
internal class PrivateSession(private val onError:(String)->Unit) {
    private var profile:Profile?=null
    var cleaning by mutableIntStateOf(0); private set
    var cleanupError by mutableStateOf(""); private set
    val available:Boolean get()=supported()

    fun attach(web:WebView) {
        check(supported()) { "請先更新 Android System WebView／Chrome，再使用隔離的無痕分頁" }
        sweepAbandoned()
        val name=profile?.name?:PREFIX+UUID.randomUUID()
        // Must be set before navigating, evaluating JS or attaching document scripts.
        WebViewCompat.setProfile(web,name)
        profile=WebViewCompat.getProfile(web)
    }

    /** Call only after destroying every WebView belonging to this session. */
    fun finish() {
        val retired=profile?:return
        profile=null // A future session always gets a new, unrelated profile.
        cleaning++
        try {
            WebStorageCompat.deleteBrowsingData(retired.webStorage) {
                // Some WebView versions cannot delete a profile loaded in this process.
                // Its website data is already cleared; remove the empty shell on next startup.
                runCatching { ProfileStore.getInstance().deleteProfile(retired.name) }
                cleaning--
            }
        } catch(_:Exception) {
            cleaning--
            cleanupError="無痕資料清理未完成；請關閉並重新開啟應用程式"
            onError(cleanupError)
        }
    }

    companion object {
        private const val PREFIX="chengjing-private-"
        private var swept=false
        fun supported()=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&
            WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
        fun sweepAbandoned() {
            if(swept||!supported())return
            // Crash/force-stop may skip onDestroy. Never reopen old private sessions.
            // Do not getProfile() before deletion: doing so can load it and block deletion.
            val profiles=ProfileStore.getInstance()
            profiles.allProfileNames.filter{it.startsWith(PREFIX)}.forEach{profiles.deleteProfile(it)}
            swept=true
        }
        fun cookies(web:WebView):CookieManager =
            if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))WebViewCompat.getProfile(web).cookieManager
            else CookieManager.getInstance()
    }
}
