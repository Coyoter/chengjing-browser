package tw.techtarian.browser

import android.webkit.WebStorage
import androidx.compose.runtime.*
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All operations run against the regular profile. Incognito has its own closing lifecycle. */
internal class BrowsingDataCleaner(private val c:BrowserController) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    var running by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    val supportsSiteData get()=WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    fun clear(range:BrowsingTimeRange,selection:BrowsingDataSelection) {
        if(running||selection.isEmpty)return
        if(selection.siteData&&!supportsSiteData){error="請先更新 Android System WebView，再刪除 Cookie 與網站資料。";return}
        val window=range.window(System.currentTimeMillis())
        val regular=c.tabs.filterNot{it.incognito}
        val closeIds=if(selection.tabs)regular.filter{window.contains(it.lastActiveAt)}.map{it.id}else emptyList()
        val historyUrls=if(selection.history)c.store.historyPages().filter{window.contains(it.visitedAt)}.map{it.url}.toSet()else emptySet()
        running=true;error=""
        scope.launch {
            try{
                if(selection.siteData){
                    // Stop outstanding loads before deleting the default profile. Never call
                    // deleteAllData as a fallback: it would leave cookies/HTTP cache behind.
                    regular.forEach{it.web.stopLoading();it.refreshContainer.isRefreshing=false}
                    withTimeout(60_000){deleteSiteData()}
                    c.icons.clear()
                }
                if(selection.history){
                    c.store.clearHistory(window)
                    regular.filter{it.url in historyUrls||window.contains(it.lastActiveAt)}.forEach{
                        it.suppressHistoryUntilNavigation=true
                        it.web.clearHistory();it.canBack=it.web.canGoBack();it.canForward=it.web.canGoForward()
                    }
                }
                closeIds.forEach{c.closeTab(it)}
                check(c.persistTabs()){"分頁變更未能儲存"}
                check(c.previews.flush()){"分頁快照尚未清理完成"}
                c.revision++
                c.sheet=""
                c.notice=if(selection.siteData)"已刪除所選瀏覽資料；Cookie、快取和網站資料已全部清除"else"已刪除${range.label}的所選瀏覽資料"
            }catch(_:TimeoutCancellationException){error="網站資料清理尚未確認完成。請稍後重試，或關閉後重新開啟 App。"}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){error="刪除未完成，部分資料可能已刪除。請重試，或關閉後重新開啟 App。"}
            finally{running=false}
        }
    }
    private suspend fun deleteSiteData()=suspendCancellableCoroutine<Unit>{continuation->
        try{
            WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()){
                if(continuation.isActive)continuation.resume(Unit)
            }
        }catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
    }
    fun close(){scope.cancel()}
}
