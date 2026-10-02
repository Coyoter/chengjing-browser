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
        if(selection.siteData&&!supportsSiteData){error=bt(R.string.msg_1e7ffc2ced5b);return}
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
                    (c.context as? MainActivity)?.imageActions?.clearShared()
                }
                if(selection.history){
                    c.store.clearHistory(window)
                    regular.filter{it.url in historyUrls||window.contains(it.lastActiveAt)}.forEach{
                        it.suppressHistoryUntilNavigation=true
                        it.web.clearHistory();it.updateNavigationState()
                    }
                }
                closeIds.forEach{c.closeTab(it)}
                check(c.persistTabs()){bt(R.string.msg_7877a2c231c9)}
                check(c.previews.flush()){bt(R.string.msg_4c3a35f40a7e)}
                c.revision++
                c.sheet=""
                c.notice=if(selection.siteData)bt(R.string.msg_a39c04ab86b5)else bt(R.string.msg_fdf0acc4c115 ,range.label)
            }catch(_:TimeoutCancellationException){error=bt(R.string.msg_6b6bc81cccc7)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){error=bt(R.string.msg_f795969c320e)}
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
