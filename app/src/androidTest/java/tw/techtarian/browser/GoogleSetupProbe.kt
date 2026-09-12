package tw.techtarian.browser

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice

/** Manual-auth probe. A passing probe is NOT proof of Drive sync. */
class GoogleSetupProbe {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun openGoogleAuthorization(){
        org.junit.Assume.assumeTrue("Google UI probe is opt-in", InstrumentationRegistry.getArguments().getString("probeGoogle")=="true")
        val c=ui.activity.controller
        ui.runOnIdle {c.sheet="sync"}
        if(!ui.activity.bookmarkSync.connected){
            ui.onNodeWithText("使用 Google 帳戶連結").performClick()
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val account=device.wait(Until.findObject(By.text("coyoter@coyoter.com")),15000)
            check(account!=null){"Expected account chooser was not shown"}
            account.click()
            Thread.sleep(8000)
        }else{
            val end=System.currentTimeMillis()+50000
            while(ui.activity.bookmarkSync.busy && System.currentTimeMillis()<end)Thread.sleep(200)
            ui.waitForIdle();Thread.sleep(1000)
        }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun walk(n:AccessibilityNodeInfo?,depth:Int=0){
            if(n==null||depth>20)return
            val text=n.text?.toString().orEmpty();val desc=n.contentDescription?.toString().orEmpty()
            if(text.isNotEmpty()||desc.isNotEmpty())Log.i("CJGoogleProbe","${n.packageName} | $text | $desc")
            for(i in 0 until n.childCount)walk(n.getChild(i),depth+1)
        }
        walk(automation.rootInActiveWindow)
        Log.i("CJGoogleProbe","Native status: ${ui.activity.bookmarkSync.status}; connected=${ui.activity.bookmarkSync.connected}")
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"google-authorization.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-Google-QA")}
        val uri=ui.activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use {automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
