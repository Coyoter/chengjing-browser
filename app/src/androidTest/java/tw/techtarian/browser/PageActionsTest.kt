package tw.techtarian.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PageActionsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ui.activity.controller
    private val device get()=UiDevice.getInstance(instrumentation)

    @Before fun isolate(){
        assumeTrue("Never run against the production application",ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.stopEye();c.sheet=""}
    }
    @Suppress("DEPRECATION")
    @Test fun sharesheetUsesActionSendUrlAndPageTitle(){
        val url="https://example.com/article?id=42#part"
        val chooser=PageSharing.chooser(url,"文章標題")!!
        assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND,send.action)
        assertEquals("text/plain",send.type)
        assertEquals(url,send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("文章標題",send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull(send.`package`)
        assertNull(send.component)
        assertNull(PageSharing.chooser("about:blank","首頁"))
    }
    @Test fun shareDoesNotAppearOnTheFirstLayerOrBlankPageMenu(){
        ui.runOnIdle{c.newTab()}
        assertOriginalToolbar()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:分享").assertDoesNotExist()
        ui.onNodeWithContentDescription("關閉選單").performClick()
        assertOriginalToolbar()
    }
    @Test fun onlyOverflowMenuInvokesAndroidChooserWithoutSendingToARealRecipient(){
        fixture(false)
        assertOriginalToolbar()
        ui.onNodeWithTag("menu-row:分享").assertDoesNotExist()
        val monitor=instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER),Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        try{
            ui.onNodeWithContentDescription("瀏覽器選單").performClick()
            ui.onNodeWithTag("menu-row:分享").performScrollTo().assertIsDisplayed()
            screenshot("overflow-share")
            ui.onNodeWithTag("menu-row:分享").performClick()
            ui.waitUntil(5000){monitor.hits>0}
            assertEquals(1,monitor.hits)
            ui.onNodeWithTag("browser-panel").assertDoesNotExist()
            assertOriginalToolbar()
        }finally{instrumentation.removeMonitor(monitor)}
    }
    @Test fun originalToolbarSurvivesBothThemesAndAddressPositions(){
        var originalTheme="system"
        var originalBottom=false
        ui.runOnIdle{
            originalTheme=c.store.theme
            originalBottom=c.store.addressAtBottom
            c.newTab()
        }
        try{
            for(theme in listOf("light","dark"))for(bottom in listOf(false,true)){
                ui.runOnIdle{c.store.theme=theme;c.store.addressAtBottom=bottom}
                ui.activityRule.scenario.recreate()
                assertOriginalToolbar()
                val controls=ui.onNodeWithTag("browser-controls").fetchSemanticsNode().boundsInRoot
                val address=ui.onNodeWithTag("browser-topbar").fetchSemanticsNode().boundsInRoot
                assertEquals("Toolbar/address order must stay unchanged",bottom,controls.center.y<address.center.y)
                screenshot("toolbar-$theme-${if(bottom)"top"else"bottom"}")
            }
        }finally{
            ui.runOnIdle{c.store.theme=originalTheme;c.store.addressAtBottom=originalBottom}
        }
    }
    private fun assertOriginalToolbar(){
        ui.onNodeWithTag("browser-controls").assertIsDisplayed().assertHeightIsEqualTo(64.dp)
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.onNodeWithContentDescription("分享目前網頁").assertDoesNotExist()
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        val eye=ui.onNode(hasText("天眼") and hasAnyAncestor(hasTestTag("browser-controls")))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Sky Eye must be the original horizontal capsule, not a round 48dp button",eye.width>eye.height*1.5f)
        ui.onNodeWithTag("quick-favorite").assertIsDisplayed()
        ui.onNodeWithContentDescription("瀏覽器選單").assertIsDisplayed()
    }
    private fun screenshot(name:String){
        require(name.matches(Regex("[a-z-]+")))
        ui.waitForIdle()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use{it.readText()}
        val directory="/data/local/tmp/chengjing-ui"
        val path="$directory/$name.png"
        // executeShellCommand does not parse shell operators such as &&.
        // Use individual commands and capture directly as the shell UID, avoiding
        // scoped-storage copies and retaining screenshots after QA app uninstall.
        shell("mkdir -p $directory")
        shell("screencap -p $path")
        val bytes=shell("stat -c %s $path").trim().toLongOrNull()?:0L
        assertTrue("Screenshot was not preserved for visual review: $path ($bytes bytes)",bytes>0L)
    }
    @Test fun plainImageLongPressOffersDownload(){checkImageMenu(false)}
    @Test fun linkedImageDownloadsItsSourceNotItsAnchor(){checkImageMenu(true)}
    @Test fun systemQueueDeclinesPageLocalUrlsWithARoutingMessage(){
        ui.runOnIdle{
            assertNull(ui.activity.imageDownloads.download("blob:https://example.com/test","https://example.com/","QA"))
            assertTrue(c.notice.contains("系統下載服務"))
            assertFalse(c.notice.contains("尚未支援"))
        }
    }
    @Test fun skyEyeSelectionDoesNotOpenTheBrowserContextMenu(){
        fixture(false)
        ui.runOnIdle{
            val tab=c.active!!
            tab.web.selecting=true
            try{assertFalse(PageContextMenu(c,tab).onLongClick())}finally{tab.web.selecting=false}
        }
    }
    private fun checkImageMenu(linked:Boolean){
        val expected=fixture(linked)
        val downloaded=AtomicReference<String?>(null)
        ui.runOnIdle{PageContextMenu(c,c.active!!){url,_,_->downloaded.set(url)}.install()}
        longPressImage()
        val option=device.wait(Until.findObject(By.text("下載圖片")),5000)
        assertNotNull("Image menu must contain Download image",option)
        if(linked)assertTrue(device.hasObject(By.text("在新分頁開啟")))
        option!!.click()
        ui.waitUntil(5000){downloaded.get()!=null}
        assertEquals(expected,downloaded.get())
        assertNotEquals("https://example.com/linked-page",downloaded.get())
    }
    private fun fixture(linked:Boolean):String{
        val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
        val png=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        bitmap.recycle()
        val src="data:image/png;base64,"+Base64.encodeToString(png,Base64.NO_WRAP)
        val image="<img id='picture' src='$src' width='160' height='160' style='display:block'>"
        val body=if(linked)"<a href='https://example.com/linked-page'>$image</a>"else image
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>圖片操作測試</title></head><body style='margin:24px'>$body</body></html>"
        ui.runOnIdle{c.newTab()!!.web.loadDataWithBaseURL("https://example.com/fixture/",html,"text/html","UTF-8",null)}
        ui.waitUntil(15000){eval("!!document.querySelector('#picture') && document.querySelector('#picture').complete && document.querySelector('#picture').naturalWidth>0")=="true"}
        ui.onNodeWithTag("web-content").assertIsDisplayed()
        return src
    }
    private fun eval(code:String):String{
        val done=CountDownLatch(1)
        val result=AtomicReference("null")
        ui.runOnUiThread{c.active!!.web.evaluateJavascript(code){result.set(it);done.countDown()}}
        assertTrue("JavaScript callback timed out",done.await(5,TimeUnit.SECONDS))
        return result.get()
    }
    private fun longPressImage(){
        val drawn=CountDownLatch(1)
        ui.runOnUiThread{c.active!!.web.postVisualStateCallback(1,object:android.webkit.WebView.VisualStateCallback(){override fun onComplete(id:Long){drawn.countDown()}})}
        assertTrue("Wait for the visible image before injecting a long press",drawn.await(8,TimeUnit.SECONDS))
        ui.waitForIdle()
        val position=JSONObject(eval("(()=>{const r=document.querySelector('#picture').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio};})()"))
        val screen=IntArray(2)
        ui.runOnUiThread{c.active!!.web.getLocationOnScreen(screen)}
        val x=screen[0]+(position.getDouble("x")*position.getDouble("dpr")).toFloat()
        val y=screen[1]+(position.getDouble("y")*position.getDouble("dpr")).toFloat()
        val down=SystemClock.uptimeMillis()
        fun send(action:Int){
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
            try{instrumentation.sendPointerSync(event)}finally{event.recycle()}
        }
        send(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong()+300)
        send(MotionEvent.ACTION_UP)
        instrumentation.waitForIdleSync()
        // The menu now uses the app's Compose theme. Advance its test frame clock,
        // which UiAutomator's native-window polling does not advance itself.
        ui.waitForIdle()
        ui.waitUntil(5000){ui.onAllNodesWithText("下載圖片").fetchSemanticsNodes().isNotEmpty()}
    }
}
