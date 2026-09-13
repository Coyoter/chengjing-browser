package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Run prepare and verify as separate instrumentation processes with am force-stop in between. */
class CertificateProcessRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val origin="https://10.0.2.2:18743/"
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String{val done=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;done.countDown()}};check(done.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(150)};assertTrue("Restart condition failed: ${c.active?.error}",check())}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(600)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-1.0.0-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun persistentExceptionAcrossProcessRestart(){
        val phase=InstrumentationRegistry.getArguments().getString("certificateRestartPhase")
        org.junit.Assume.assumeTrue(phase in setOf("prepare","verify"))
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        if(phase=="prepare")main{c.store.setCertificateException(origin,true)}
        assertTrue("Saved exception must survive a new process",BrowserStore(ui.activity).certificateException(origin))
        main{c.newTab(origin+"main?phase="+phase)}
        until{eval("!!document.querySelector('#tls-main')")=="true"&&c.active!!.progress==100&&c.active!!.error.isEmpty()}
        ui.onNodeWithContentDescription("憑證例外已開啟，點擊查看").assertExists()
        if(phase=="verify"){
            ui.onNodeWithContentDescription("憑證例外已開啟，點擊查看").performClick()
            ui.onNodeWithTag("certificate-exception:${CertificateExceptions.site(origin)}").assertIsOn()
            shot("persistent-certificate-settings")
            ui.onNodeWithContentDescription("關閉選單").performClick()
            main{c.sheet="privacy"}
            ui.onNodeWithText("隱私與資料").assertExists()
            shot("privacy-panel")
            main{c.store.setCertificateException(origin,false);c.tabs.forEach{it.web.clearSslPreferences()}}
        }
    }
}
