package tw.techtarian.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object BrowserUserAgent {
    fun mobile(webViewAgent:String):String = webViewAgent.replace("; wv","").replace(" Version/4.0","")
    fun desktop(webViewAgent:String):String {
        val chrome=Regex("Chrome/([0-9.]+)").find(webViewAgent)?.groupValues?.get(1)
        return if(chrome!=null)"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
        else mobile(webViewAgent).replace(" Mobile","")
    }
}

data class BlockedEvent(val kind:String,val url:String,val time:Long=System.currentTimeMillis()) {
    // A blocked window can retain its original WebView request (including POST/opener).
    internal var openWindow by androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)
}
