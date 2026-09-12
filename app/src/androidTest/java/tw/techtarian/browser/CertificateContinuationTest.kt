package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CertificateContinuationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun fixtureIsExplicit(){org.junit.Assume.assumeTrue("TLS fixture test is opt-in",InstrumentationRegistry.getArguments().getString("tlsFixture")=="true")}
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String{val done=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;done.countDown()}};check(done.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(150)};assertTrue("Condition timed out: error=${c.active?.error}; warning=${c.active?.certificateWarning}; pending=${c.active?.pendingUrl}; state="+eval("JSON.stringify({url:location.href,image:document.querySelector('#certificate-image')?.naturalWidth,complete:document.querySelector('#certificate-image')?.complete})"),check())}
    private fun open(url:String){main{c.newTab(url)};until{c.active!!.progress==100&&eval("document.readyState")=="\"complete\""}}
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(500);val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.4-QA")};val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun recoverableMainCertificateLoadsAndRetainsWarning(){
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        open("https://10.0.2.2:18743/main")
        until{eval("!!document.querySelector('#tls-main')")=="true"}
        assertTrue(c.active!!.certificateWarning.isNotEmpty());assertEquals("",c.active!!.error)
        ui.onNodeWithContentDescription("憑證異常，點擊查看").assertExists()
        ui.onNodeWithText("暫時連不上這個網站").assertDoesNotExist()
        shot("01-main-certificate-continued")
        val old=eval("performance.timeOrigin");main{c.reload()};until{eval("performance.timeOrigin")!=old&&eval("!!document.querySelector('#tls-main')")=="true"}
        assertTrue(c.active!!.certificateWarning.isNotEmpty())
        ui.activityRule.scenario.recreate()
        until{eval("!!document.querySelector('#tls-main')")=="true"&&c.active!!.certificateWarning.isNotEmpty()}
        ui.onNodeWithContentDescription("憑證異常，點擊查看").performClick()
        ui.onNodeWithText("無法確認連線對象身分",substring=true).assertExists()
        shot("02-certificate-details")
    }
    @Test fun badImageNeverReplacesValidMainPageAndCachedBypassStaysVisible(){
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        open("https://example.com/")
        until{eval("document.querySelector('h1')?.textContent")=="\"Example Domain\""}
        eval("const img=document.createElement('img');img.id='certificate-image';img.src='https://127.0.0.1:18743/pixel';document.body.append(img)")
        until{eval("document.querySelector('#certificate-image')?.naturalWidth > 0")=="true"&&c.active!!.certificateWarning.isNotEmpty()}
        assertEquals("",c.active!!.error)
        assertEquals("\"Example Domain\"",eval("document.querySelector('h1').textContent"))
        ui.onNodeWithText("暫時連不上這個網站").assertDoesNotExist()
        ui.onNodeWithContentDescription("憑證異常，點擊查看").assertExists()
        shot("03-subresource-keeps-page")
        // Another valid origin loading the now-cached bad-cert host must still show the warning.
        open("https://example.org/")
        until{eval("document.querySelector('h1')?.textContent")=="\"Example Domain\""}
        assertEquals("",c.active!!.certificateWarning)
        eval("const img=document.createElement('img');img.id='certificate-image';img.src='https://127.0.0.1:18743/pixel?cached=1';document.body.append(img)")
        until{eval("document.querySelector('#certificate-image')?.naturalWidth > 0")=="true"&&c.active!!.certificateWarning.isNotEmpty()}
        assertEquals("",c.active!!.error)
        shot("04-cached-subresource-warning")
        open("https://www.google.com/")
        until{c.active!!.progress==100}
        assertEquals("",c.active!!.certificateWarning)
        assertEquals("",c.active!!.error)
    }
}
