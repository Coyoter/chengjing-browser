package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BookmarkEditJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+16000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue("Bookmark state did not update",check())}
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(300)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.13-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun editableUrlValidatesCancelsPersistsAndOpensTheNewPage(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        val stamp=UUID.randomUUID().toString().take(8);val folder="編輯測試 $stamp"
        val oldUrl="https://practice.chengjing.invalid/old-$stamp"
        val newUrl="https://practice.chengjing.invalid/updated-$stamp?q=1#reading"
        val title="閱讀書籤 $stamp";val newTitle="最新閱讀進度 $stamp"
        val before=c.store.bookmarkStore.all()
        main{c.store.bookmarkStore.add(oldUrl,title,folder);c.revision++;c.sheet="bookmarks"}
        val original=c.store.bookmarkStore.visible().single{it.url==oldUrl}
        fun openEditor(name:String){ui.onNodeWithContentDescription("編輯書籤 $name").performClick();ui.onNodeWithTag("bookmark-edit-url").assertExists()}
        ui.onNodeWithText(folder,substring=false).performClick();openEditor(title)
        ui.onNodeWithTag("bookmark-edit-url").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString(oldUrl)))
        shot("01-edit-prefilled-url")
        ui.onNodeWithTag("bookmark-edit-url").performTextReplacement(newUrl)
        ui.onNodeWithText("取消",substring=false).performClick()
        assertEquals(original,c.store.bookmarkStore.visible().single{it.id==original.id})
        openEditor(title)
        ui.onNodeWithTag("bookmark-edit-url").performTextReplacement("javascript:alert(1)")
        ui.onNodeWithText("儲存",substring=false).assertIsNotEnabled()
        ui.onNodeWithText("請輸入以 https:// 或 http:// 開頭的網址").assertExists();shot("02-invalid-url")
        assertEquals(original,c.store.bookmarkStore.visible().single{it.id==original.id})
        ui.onNodeWithTag("bookmark-edit-url").performTextReplacement("")
        ui.onNodeWithText("儲存",substring=false).assertIsNotEnabled()
        ui.onNodeWithTag("bookmark-edit-url").performTouchInput{click()}
        ui.onNodeWithTag("bookmark-edit-url").performTextReplacement(newUrl)
        ui.onNodeWithTag("bookmark-edit-title").performTextReplacement(newTitle)
        ui.onNodeWithText("儲存",substring=false).assertIsDisplayed();ui.onNodeWithText("儲存",substring=false).performClick()
        until{c.store.bookmarkStore.visible().any{it.url==newUrl}}
        val changed=c.store.bookmarkStore.visible().single{it.url==newUrl}
        assertEquals(folder,changed.folder);assertEquals(newTitle,changed.title)
        assertTrue(c.store.bookmarkStore.all().single{it.id==original.id}.deleted)
        assertFalse(c.store.bookmarkStore.visible().any{it.url==oldUrl})
        assertEquals(before,c.store.bookmarkStore.all().filter{it.id!=original.id&&it.id!=changed.id})
        val reopened=BookmarkStore(ui.activity)
        assertEquals(changed,reopened.visible().single{it.id==changed.id})
        main{c.store.bookmarkStore.mergeRemote(listOf(original));c.revision++}
        assertFalse(c.store.bookmarkStore.visible().any{it.url==oldUrl})
        openEditor(newTitle);ui.onNodeWithTag("bookmark-edit-url").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString(newUrl)));shot("03-saved-new-url")
        ui.onNodeWithText("取消",substring=false).performClick()
        ui.onNodeWithText(newTitle,substring=false).performClick()
        until{c.active!!.url==newUrl&&eval("location.href")=="\"$newUrl\""&&eval("document.readyState")=="\"complete\""}
        assertEquals("",c.sheet)
    }
}
