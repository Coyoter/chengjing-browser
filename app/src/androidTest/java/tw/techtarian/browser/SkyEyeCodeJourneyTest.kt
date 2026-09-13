package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SkyEyeCodeJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(120)};assertTrue(check())}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(300)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.15-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun directCssAndJsActionsPreserveDraftRulesAndAddPageContent(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        main{c.newTab("https://practice.chengjing.invalid/")}
        until{c.domain.isNotEmpty()&&eval("document.readyState")=="\"complete\""};ui.waitForIdle()
        ui.onNodeWithText("天眼",substring=false).performClick();until{c.eye}
        ui.onNodeWithText("想做什麼嘗試？直接問 AI").assertIsDisplayed();shot("01-skyeye-actions")
        main{c.previewSite(c.site.copy(rules=listOf(ElementRule("#sample-overlay","浮動元件"))))}
        main{c.sheet="code"}
        ui.onNodeWithTag("custom-css-input").assertExists()
        ui.onNodeWithTag("custom-css-input").performTextReplacement("h1 { color: rgb(20, 110, 90) !important; }")
        ui.onNodeWithText("儲存並套用").performScrollTo().performClick()
        until{!c.eye&&eval("document.readyState")=="\"complete\""&&eval("getComputedStyle(document.querySelector('h1')).color")=="\"rgb(20, 110, 90)\""}
        assertEquals("#sample-overlay",c.site.rules.single().selector)
        assertEquals("\"none\"",eval("getComputedStyle(document.querySelector('#sample-overlay')).display"))
        ui.onNodeWithText("天眼",substring=false).performClick();main{c.sheet="code"}
        ui.onNodeWithTag("custom-js-input").assertExists()
        val js="const note=document.createElement('p');note.id='reading-note';note.textContent='我的閱讀備註';document.querySelector('main').prepend(note);"
        ui.onNodeWithTag("custom-js-input").performTextReplacement(js)
        ui.onNodeWithText("儲存並套用").performScrollTo().performClick()
        until{eval("document.querySelector('#reading-note')?.textContent")=="\"我的閱讀備註\""}
        assertEquals(js,c.site.js);assertTrue(c.site.css.contains("110"));assertEquals(1,c.site.rules.size);shot("02-added-content")
        val previousDocument=eval("performance.timeOrigin")
        main{c.reload()};until{eval("performance.timeOrigin")!=previousDocument&&eval("document.querySelectorAll('#reading-note').length")=="1"&&eval("document.readyState")=="\"complete\""}
    }
}
