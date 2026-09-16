package tw.techtarian.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** Disposable profile; never clear or reuse the default profile for private browsing.
 * WebView may use temporary on-disk data. Clear on last close and clean interrupted
 * sessions on the next launch. This is not disk encryption or network anonymity.
 */
@SuppressLint("RequiresFeature")
internal class IncognitoSession(private val report:(String)->Unit){
    companion object {
        private const val PREFIX="chengjing-private-"
        private val live=mutableSetOf<String>()
    }
    val supported:Boolean get()=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
    private var name:String?=null
    fun cleanupStaleProfiles(){
        if(!supported)return
        val store=ProfileStore.getInstance()
        store.allProfileNames.filter{it.startsWith(PREFIX)&&it !in live}.forEach{
            runCatching{store.deleteProfile(it)}.onFailure{report("有無痕暫存資料尚待清理；本次不會重新使用舊工作階段")}
        }
    }
    fun attach(web:WebView){
        check(supported){"需要支援隔離與資料清除的 WebView"}
        val current=name?:run{cleanupStaleProfiles();(PREFIX+UUID.randomUUID()).also{name=it;live.add(it)}}
        // Must be the FIRST WebView operation after construction.
        WebViewCompat.setProfile(web,current)
    }
    fun profileOf(web:WebView):Profile?=if(supported)WebViewCompat.getProfile(web)else null
    fun end(profile:Profile?){
        val old=name?:return
        name=null
        if(profile==null){live.remove(old);cleanupStaleProfiles();return}
        try{
            WebStorageCompat.deleteBrowsingData(profile.webStorage){
                live.remove(old)
                // All WebViews have been destroyed. Some providers retain the loaded
                // handle; its data is cleared, and the empty directory is removed
                // at the next cold start without loading that profile again.
                runCatching{ProfileStore.getInstance().deleteProfile(old)}
            }
        }catch(_:Exception){
            live.remove(old)
            report("無痕資料清理未完成，請關閉並重新開啟瀏覽器；舊工作階段不會恢復")
        }
    }
}
