package tw.techtarian.browser
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GemmaLocalTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(15,TimeUnit.SECONDS));return result}
    @Before fun optIn(){Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("gemmaLive")=="true");Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));main{c.tabs.toList().drop(1).forEach{c.closeTab(it.id)}}}
    @Test fun prepareFixtureDirectory(){assertTrue(c.gemma.modelFile.parentFile!!.mkdirs()||c.gemma.modelFile.parentFile!!.isDirectory)}
    @Test fun nativeGemmaGeneratesAndAppliesHtmlWithoutApiKey(){
        android.util.Log.i("CJGemmaQA","Model path=${c.gemma.modelFile}; bytes=${c.gemma.modelFile.length()}; readable=${c.gemma.modelFile.canRead()}")
        runBlocking{c.gemma.verifyModel()}
        main{c.store.aiProvider="gemma";c.store.saveKey("");c.store.save(SiteRules("chengjing.invalid"));c.newTab("https://practice.chengjing.invalid/")}
        ui.waitUntil(20000){eval("document.readyState")=="\"complete\""&&eval("innerWidth>300")=="true"}
        main{c.beginEye();c.chooseSelector("#reading-card")}
        ui.waitUntil(10000){c.selection?.selector=="#reading-card"}
        ui.onNodeWithText("我這樣改對嗎？請 AI 檢查").performScrollTo().performClick()
        ui.onNodeWithTag("developer-ai-problem").performTextReplacement("在此元件最後新增一段文字：本機測試完成。使用 HTML，保留原有內容。")
        ui.onNodeWithText("使用 Gemma 4 分析").performScrollTo().performClick()
        ui.waitUntil(240000){ui.onAllNodesWithText("一鍵套用建議").fetchSemanticsNodes().isNotEmpty()||ui.onAllNodesWithTag("developer-error").fetchSemanticsNodes().isNotEmpty()}
        val errors=ui.onAllNodesWithTag("developer-error").fetchSemanticsNodes()
        assertTrue("Local inference failed: "+errors.joinToString{it.config.toString()},errors.isEmpty())
        ui.onNodeWithText("一鍵套用建議").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(20000){eval("document.querySelector('#reading-card').textContent.includes('本機測試完成')")=="true"}
        assertFalse(c.store.hasKey());assertTrue(c.site.edits.isNotEmpty())
        android.util.Log.i("CJGemmaQA","Real local Gemma inference -> validated proposal -> DOM application passed; no API key")
    }
    @Test fun nativeDownloaderVerifiesModelAndSurvivesRecreation(){
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("gemmaDownload")=="true")
        main{c.gemma.removeModel();c.gemma.startDownload(false)}
        ui.waitUntil(900000){c.gemma.ready||c.gemma.error.isNotBlank()}
        assertTrue("Download failed: ${c.gemma.status} ${c.gemma.error}",c.gemma.ready)
        assertEquals(GemmaLocal.MODEL_BYTES,c.gemma.modelFile.length())
        ui.activityRule.scenario.recreate();ui.waitUntil{c.gemma.ready}
        android.util.Log.i("CJGemmaQA","Native DownloadManager -> SHA256 verification -> activity recreation passed")
    }
}
