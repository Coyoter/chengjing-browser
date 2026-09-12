package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MenuDesignTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue(check())}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(400)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.10-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun open(){main{c.newTab("https://practice.chengjing.invalid/")};until{c.active!!.url.startsWith("https://practice.chengjing.invalid")&&eval("document.readyState")=="\"complete\""};ui.waitForIdle()}
    @Before fun isolated(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun groupedMenuKeepsLongTitlesUniformAndSettingsWork(){
        open()
        main{
            val favorite=c.favorites.save(c.active!!.url,"一個非常長的小說名稱與最新閱讀章節：".repeat(12),0.0,0.0)
            c.active!!.favoriteId=favorite.id;c.revision++;c.sheet="menu"
        }
        ui.waitForIdle()
        val favorite=ui.onNodeWithTag("menu-row:更新收藏進度").fetchSemanticsNode().boundsInRoot
        val bookmark=ui.onNodeWithTag("menu-row:加入書籤").fetchSemanticsNode().boundsInRoot
        assertEquals(bookmark.width,favorite.width,.5f);assertEquals(bookmark.height,favorite.height,.5f)
        assertEquals(64*ui.activity.resources.displayMetrics.density,favorite.height,1f)
        val widths=listOf("新增分頁","收藏","書籤","瀏覽紀錄").map{ui.onNodeWithTag("menu-shortcut:$it").fetchSemanticsNode().boundsInRoot.width}
        assertTrue(widths.max()-widths.min()<=1f)
        val panel=ui.onNodeWithTag("browser-panel").fetchSemanticsNode().boundsInWindow
        assertEquals(0f,panel.left,1f);assertEquals(0f,panel.top,1f)
        assertEquals(ui.activity.resources.displayMetrics.widthPixels.toFloat(),panel.width,1f)
        assertEquals(ui.activity.resources.displayMetrics.heightPixels.toFloat(),panel.height,1f)
        val insets=androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
        assertTrue("Header must clear camera and status bar",ui.onNodeWithTag("panel-header").fetchSemanticsNode().boundsInWindow.top>=insets.top)
        ui.onNodeWithTag("sheet-drag-handle").assertDoesNotExist();shot("01-menu-light-long-title")
        ui.onNodeWithText("設定",substring=false).performScrollTo().performClick();ui.waitForIdle()
        val settingsWidth=ui.onNodeWithTag("browser-panel").fetchSemanticsNode().boundsInWindow.width
        assertEquals(panel.width,settingsWidth,1f)
        ui.onNodeWithText("網址列在下方").performScrollTo().performClick();assertTrue(c.store.addressAtBottom)
        ui.onNodeWithText("深色",substring=false).performClick();assertEquals("dark",c.store.theme);shot("02-appearance-dark")
        val appearanceBounds=ui.onNodeWithTag("browser-panel").fetchSemanticsNode().boundsInWindow
        ui.onNodeWithText("AI",substring=false).performClick();ui.onNodeWithText("OpenRouter 連線").assertIsDisplayed()
        val aiBounds=ui.onNodeWithTag("browser-panel").fetchSemanticsNode().boundsInWindow
        assertEquals(appearanceBounds.top,aiBounds.top,1f);assertEquals(appearanceBounds.height,aiBounds.height,1f)
        shot("03-ai-settings")
        ui.onNodeWithText("瀏覽",substring=false).performClick();shot("04-browsing-settings")
        ui.onNodeWithText("瀏覽器識別（User-Agent）").performClick();ui.waitForIdle()
        assertEquals(panel.width,ui.onNodeWithTag("browser-panel").fetchSemanticsNode().boundsInWindow.width,1f)
        ui.onNodeWithContentDescription("返回上一層").performClick();ui.onNodeWithText("網站顯示").assertIsDisplayed()
        ui.onNodeWithContentDescription("返回上一層").performClick();ui.onNodeWithText("目前頁面").assertIsDisplayed();shot("05-menu-dark")
        ui.onNodeWithText("天眼設定",substring=false).performScrollTo().performClick();shot("06-eye-settings")
        ui.onNodeWithContentDescription("返回上一層").performClick()
        ui.onNodeWithText("Google 書籤同步").performScrollTo().performClick();shot("07-sync-panel")
        ui.onNodeWithContentDescription("返回上一層").performClick();assertEquals("menu",c.sheet)
        ui.onNodeWithTag("menu-shortcut:書籤").performClick();ui.onNodeWithContentDescription("匯入 Chrome 書籤").assertExists()
        main{c.sheet=""}
    }
    @Test fun aiSettingsRemainUsableWithTheKeyboard(){
        open();main{c.sheet="settings"}
        ui.onNodeWithText("AI",substring=false).performClick()
        ui.onNode(hasSetTextAction() and hasText("API Key",substring=true)).performTextInput("sk-local-qa-no-network")
        ui.onNodeWithText("儲存 API Key").performScrollTo().assertIsDisplayed();shot("08-ai-keyboard")
        ui.onNodeWithText("儲存 API Key").performClick();until{c.store.hasKey()}
        assertEquals("sk-local-qa-no-network",c.store.readKey())
        ui.onNodeWithText("移除已儲存的 API Key").performScrollTo().performClick();until{!c.store.hasKey()}
        val original=c.store.model
        ui.onNodeWithText("Gemini",substring=false).performScrollTo().performClick()
        assertTrue(c.store.model.startsWith("google/"))
        main{c.store.model=original;c.sheet=""}
    }

}
