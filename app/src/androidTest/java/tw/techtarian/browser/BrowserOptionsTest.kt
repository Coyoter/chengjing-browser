package tw.techtarian.browser

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BrowserOptionsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String{val latch=CountDownLatch(1);var raw="";main{c.active!!.web.evaluateJavascript(script){raw=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return raw}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+16000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(120)};assertTrue("Condition timed out",check())}
    private fun open(){main{c.newTab("https://practice.chengjing.invalid/")};until{c.active!!.url.startsWith("https://practice.chengjing.invalid")&&eval("document.readyState")=="\"complete\""};ui.waitForIdle()}
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(500);val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.12-QA")};val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Before fun isolated(){org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun popupBlockingOnlyAddsAnIndicatorAndThePageRemainsClickable(){
        open();main{c.notice="";c.saveSite(c.site.copy(guard=true))}
        eval("window.open('https://example.com/popup');location.href='https://example.com/automatic';")
        until{c.active!!.blockedTotal==2}
        ui.onNodeWithTag("blocking-indicator").assertExists()
        ui.onNodeWithText("已攔下新視窗",substring=true).assertDoesNotExist()
        assertEquals("",c.notice)
        eval("document.body.insertAdjacentHTML('beforeend','<button id=quiet-target style=\"position:fixed;bottom:14px;left:20px;z-index:99999\" onclick=\"window.clickedBehindNotice=true\">仍然可點</button>')")
        val p=JSONObject(eval("(()=>{const r=document.querySelector('#quiet-target').getBoundingClientRect();return{x:(r.x+r.width/2)*devicePixelRatio,y:(r.y+r.height/2)*devicePixelRatio}})()"))
        main{val t=SystemClock.uptimeMillis();val x=p.getDouble("x").toFloat();val y=p.getDouble("y").toFloat();c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0));c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,x,y,0))}
        until{eval("window.clickedBehindNotice")=="true"}
        shot("07-quiet-blocking")
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithText("已攔截 2 次干擾").performClick()
        ui.onNodeWithText("新視窗／彈窗").assertExists();ui.onNodeWithText("自動跳轉").assertExists();shot("08-block-log")
    }
    @Test fun userAgentHasChromeOriginalAndCustomOptions(){
        open()
        main{c.sheet="user-agent"}
        ui.onNodeWithText("原始 Android WebView").performClick();ui.onNodeWithText("儲存並重新載入").performClick()
        until{eval("navigator.userAgent.includes('; wv')")=="true"}
        main{c.sheet="user-agent"};ui.onNodeWithText("Chrome 手機版（預設）").performClick();ui.onNodeWithText("儲存並重新載入").performClick()
        until{eval("!navigator.userAgent.includes('; wv') && navigator.userAgent.includes('Chrome/') && !navigator.userAgent.includes('Version/4.0')")=="true"}
        main{c.sheet="user-agent"};ui.onNodeWithText("自訂 User-Agent").performClick()
        ui.onNodeWithTag("custom-user-agent").performTextReplacement("Mozilla/5.0 ChengJing-Test/1.0")
        ui.onNodeWithText("儲存並重新載入").performScrollTo().performClick()
        until{eval("navigator.userAgent")=="\"Mozilla/5.0 ChengJing-Test/1.0\""}
        assertEquals("custom",BrowserStore(ui.activity).userAgentMode)
        main{c.sheet="user-agent"};shot("09-user-agent")
        main{c.setUserAgent("chrome","");c.sheet=""}
    }
    @Test fun addressBarCanMoveBelowAndKeepsSelectAll(){
        open()
        fun checkNewTab(){
            ui.waitForIdle()
            val address=ui.onNodeWithTag("address-capsule",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val plus=ui.onNodeWithTag("new-tab-button").fetchSemanticsNode().boundsInRoot
            val tabs=ui.onNodeWithTag("tab-switcher").fetchSemanticsNode().boundsInRoot
            assertTrue(address.right<=plus.left);assertTrue(plus.right<=tabs.left)
            assertEquals(address.center.y,plus.center.y,1f);assertEquals(tabs.center.y,plus.center.y,1f)
            val original=c.active!!;val url=original.url;val before=c.tabs.size
            ui.onNodeWithTag("new-tab-button").performTouchInput{click()}
            until{c.tabs.size==before+1};ui.waitForIdle()
            val created=c.active!!
            assertNotEquals(original.id,created.id);assertEquals("",created.url)
            assertTrue(c.tabs.contains(original));assertEquals(url,original.url)
            ui.onNodeWithContentDescription("分頁，${before+1} 個").assertExists()
            main{c.switchTab(original.id);c.closeTab(created.id)}
        }
        checkNewTab();shot("12-top-address-plus")
        main{c.sheet="settings"}
        ui.onNodeWithText("網址列在下方").performScrollTo().performClick();main{c.sheet=""};ui.waitForIdle()
        val field=ui.onNodeWithTag("address-capsule",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val controls=ui.onNodeWithTag("browser-controls",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(field.top>controls.bottom)
        assertTrue(BrowserStore(ui.activity).addressAtBottom)
        shot("10-bottom-address")
        ui.onNodeWithText("天眼",useUnmergedTree=true).performClick();until{c.eye&&c.active!!.web.selecting};assertEquals("",c.sheet)
        shot("11-top-skyeye")
        main{c.stopEye()}
        val input=ui.onNodeWithTag("address-input");input.performTouchInput{click()};ui.waitForIdle()
        assertEquals(TextRange(0,c.active!!.url.length),input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        input.performTextInput("example.com");input.assertTextEquals("example.com");input.performImeAction()
        until{c.active!!.url.startsWith("https://example.com")}
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        val after=ui.onNodeWithTag("address-capsule",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val top=ui.onNodeWithTag("browser-controls",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(after.top>top.bottom)
        checkNewTab();shot("13-bottom-address-plus")
    }
}
