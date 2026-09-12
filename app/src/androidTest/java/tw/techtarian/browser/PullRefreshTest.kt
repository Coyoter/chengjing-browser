package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PullRefreshTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String {
        val latch=CountDownLatch(1);var result=""
        main{c.active!!.web.evaluateJavascript(script){result=it;latch.countDown()}}
        check(latch.await(8,TimeUnit.SECONDS));return result
    }
    private fun until(check:()->Boolean){
        val end=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)}
        assertTrue("Condition timed out",check())
    }
    private fun open(){
        main{c.newTab("https://practice.chengjing.invalid/")}
        until{eval("document.readyState")=="\"complete\""&&eval("location.hostname")=="\"practice.chengjing.invalid\""}
        ui.waitForIdle()
    }
    private fun pull(){ui.onNodeWithTag("web-content").performTouchInput{swipeDown(startY=height*.2f,endY=height*.8f,durationMillis=650)}}
    private fun pullWithScreenshot(bottom:Boolean){
        val bounds=ui.onNodeWithTag("web-content").fetchSemanticsNode().boundsInWindow
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val started=android.os.SystemClock.uptimeMillis()
        fun touch(action:Int,fraction:Float){
            val event=android.view.MotionEvent.obtain(started,android.os.SystemClock.uptimeMillis(),action,bounds.center.x,bounds.top+bounds.height*fraction,0)
            try{check(automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
        touch(android.view.MotionEvent.ACTION_DOWN,.2f)
        for(step in 1..20){Thread.sleep(32);touch(android.view.MotionEvent.ACTION_MOVE,.2f+.6f*step/20)}
        Thread.sleep(200)
        val values=android.content.ContentValues().apply{
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"12-pull-refresh-${if(bottom)"bottom" else "top"}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.5-QA")
        }
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        touch(android.view.MotionEvent.ACTION_UP,.8f)
    }
    @Before fun isolated(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}

    @Test fun topPullReloadsInBothToolbarPositionsAndSmallDragCancels(){
        open()
        for(bottom in listOf(false,true)){
            main{c.sheet="settings"}
            ui.onNodeWithText(if(bottom)"網址列在下方" else "網址列在上方").performScrollTo().performClick()
            main{c.sheet=""};ui.waitForIdle()
            eval("scrollTo(0,0)");until{eval("scrollY")=="0"}
            val before=eval("performance.timeOrigin")
            ui.onNodeWithTag("web-content").performTouchInput{swipeDown(startY=height*.2f,endY=height*.2f+20,durationMillis=400)}
            Thread.sleep(500);assertEquals(before,eval("performance.timeOrigin"))
            pullWithScreenshot(bottom)
            until{eval("performance.timeOrigin")!=before&&eval("document.readyState")=="\"complete\""}
            until{!c.active!!.refreshContainer.isRefreshing}
        }
    }

    @Test fun scrollingFromWithinPageAndSkyEyeDraftDoNotRefresh(){
        open();val before=eval("performance.timeOrigin")
        eval("scrollTo(0,150)");until{eval("scrollY").toDouble()>0 && c.active!!.web.canScrollVertically(-1)}
        pull();Thread.sleep(600)
        assertEquals(before,eval("performance.timeOrigin"))
        assertEquals("0",eval("scrollY"))
        main{c.beginEye();c.previewSite(c.site.copy(css=".refresh-test { opacity: .8; }"))}
        ui.waitForIdle();pull();Thread.sleep(500)
        assertEquals(before,eval("performance.timeOrigin"))
        assertTrue(c.eye);assertTrue(c.dirty);assertNotNull(c.draft)
        assertFalse(c.active!!.refreshContainer.isRefreshing)
        main{c.stopEye()};pull()
        until{eval("performance.timeOrigin")!=before}
        until{!c.active!!.refreshContainer.isRefreshing}
    }
}
