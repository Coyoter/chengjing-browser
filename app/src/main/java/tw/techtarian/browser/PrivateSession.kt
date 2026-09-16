package tw.techtarian.browser

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** Separate cookies/cache/JS storage. Never clear or reuse the Default profile. */
internal class PrivateSession(private val notice:(String)->Unit) {
    private var profile:Profile?=null
    private var name:String?=null
    @Volatile var clearing=false
        private set
    val supported:Boolean get()=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    init {
        // Do not load stale profiles before deletion. Crashed sessions are never restored.
        if(supported) {
            val store=ProfileStore.getInstance()
            store.allProfileNames.filter{it.startsWith(PREFIX)}.forEach{old->
                runCatching{store.deleteProfile(old)}
            }
        }
    }
    fun attach(web:WebView) {
        check(supported){"請更新 Android System WebView，才能使用資料隔離的無痕分頁"}
        val session=name?:"$PREFIX${UUID.randomUUID()}".also{name=it}
        WebViewCompat.setProfile(web,session)
        profile=WebViewCompat.getProfile(web)
    }
    fun cookies(web:WebView):CookieManager {
        check(supported)
        return WebViewCompat.getProfile(web).cookieManager
    }
    /** Invoke only after every WebView for this session has been destroyed. */
    fun clear() {
        val old=profile?:return
        val oldName=name
        profile=null;name=null
        clearing=true
        if(supported) {
            try {
                WebStorageCompat.deleteBrowsingData(old.webStorage) {
                    clearing=false
                    // Loaded profiles may remain for this process; their data was cleared.
                    if(oldName!=null)runCatching{ProfileStore.getInstance().deleteProfile(oldName)}
                }
            } catch(_:Exception) {
                clearing=false
                notice("無痕工作階段已關閉；網站資料清理未完成，請關閉並重新開啟 App")
            }
        }
    }
    companion object { const val PREFIX="chengjing-private-" }
}
