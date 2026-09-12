package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only diagnostics on a public page, not proof about any undisclosed website. */
class BrowserRenderingProbe {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun eval(script:String):String{val done=CountDownLatch(1);var raw="";InstrumentationRegistry.getInstrumentation().runOnMainSync{c.active!!.web.evaluateJavascript(script){raw=it;done.countDown()}};check(done.await(8,TimeUnit.SECONDS));return raw}
    private fun report(){
        val page=eval("JSON.stringify({ua:navigator.userAgent,viewport:innerWidth,dpr:devicePixelRatio,prefersDark:matchMedia('(prefers-color-scheme: dark)').matches,metaViewport:document.querySelector('meta[name=viewport]')?.content})")
        ui.runOnIdle{
            val native=JSONObject().put("appTheme",c.store.theme).put("textZoom",c.active!!.web.settings.textZoom).put("layoutAlgorithm",c.active!!.web.settings.layoutAlgorithm.toString()).put("webViewVersion",WebViewCompat.getCurrentWebViewPackage(ui.activity)?.versionName).put("page",org.json.JSONTokener(page).nextValue())
            android.util.Log.i("CJCompatibility",native.toString())
        }
    }
    @Test fun inspectPublicPageBeforeAndAfterUiDarkMode(){
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{c.newTab("https://example.com/")}
        val end=System.currentTimeMillis()+20000
        while(System.currentTimeMillis()<end){if(eval("document.querySelector('h1')?.textContent")=="\"Example Domain\"")break;Thread.sleep(150)}
        ui.waitForIdle()
        report()
        ui.runOnIdle{c.sheet="settings"}
        ui.onNodeWithText("深色",useUnmergedTree=true).performClick()
        ui.runOnIdle{c.sheet=""}
        report()
    }
}
