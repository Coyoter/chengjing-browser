package tw.techtarian.browser

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class BookmarkImportJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun screenshot(name:String){
        ui.waitForIdle();Thread.sleep(500)
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.3-QA")}
        val uri=ui.activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun openImport(){
        ui.runOnIdle{c.sheet=""}
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithText("匯入 Chrome 書籤",substring=false).assertDoesNotExist()
        ui.onNodeWithText("書籤",substring=false).performScrollTo().performClick()
        ui.onNodeWithContentDescription("匯入 Chrome 書籤").performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),10000))
    }
    private fun pick(filename:String){
        var file=device.wait(Until.findObject(By.text(filename)),6000)
        if(file==null){
            device.findObject(By.desc("Show roots"))?.click()
            device.wait(Until.findObject(By.text("Downloads")),3000)?.click()
            file=device.wait(Until.findObject(By.text(filename)),5000)
        }
        if(file==null){device.dumpWindowHierarchy(java.io.File(ui.activity.getExternalFilesDir(null),"import-picker.xml"));error("Fixture HTML not visible in the system picker")}
        file.click()
        // Android DocumentsUI may keep the row selected until its Select action is pressed.
        device.wait(Until.findObject(By.text("Select")),2000)?.click()
        val confirm=device.wait(Until.findObject(By.text("匯入 2 個書籤？")),10000)
        if(confirm==null){
            screenshot("12-import-picker-diagnostic")
            val tree=java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(tree)
            android.util.Log.i("CJImport",tree.toString().take(18000))
        }
        assertNotNull("System picker URI reached the real importer",confirm)
        device.findObject(By.text("匯入")).click()
        ui.waitUntil(10000){c.sheet=="bookmarks"}
    }
    @Test fun chromeHtmlViaSystemPickerPreservesFoldersDeduplicatesAndCancels(){
        org.junit.Assume.assumeTrue("This import test uses only the isolated QA application",ui.activity.packageName.endsWith(".qa"))
        val stamp=UUID.randomUUID().toString().take(8)
        val filename="Chrome-Bookmarks-$stamp.html"
        val html="""<!DOCTYPE NETSCAPE-Bookmark-file-1><META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8"><DL><p>
            <DT><H3>閱讀</H3><DL><p><DT><H3>科技</H3><DL><p>
            <DT><A HREF="https://example.com/$stamp">山與海 &amp; 科技</A>
            <DT><A HREF="https://example.com/$stamp">山與海 &amp; 科技</A></DL><p>
            <DT><A HREF="https://example.org/$stamp">稍後閱讀</A></DL><p></DL><p>"""
        val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,filename);put(MediaStore.Downloads.MIME_TYPE,"text/html");put(MediaStore.Downloads.RELATIVE_PATH,"Download")}
        val uri=ui.activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{it.write(html.toByteArray())}
        val before=c.store.bookmarkStore.visible().map{it.id}.toSet()
        openImport();pick(filename)
        val rows=c.store.bookmarkStore.visible().filter{it.url.contains(stamp)}
        assertEquals(2,rows.size)
        assertEquals("閱讀 / 科技",rows.single{it.title=="山與海 & 科技"}.folder)
        assertEquals("閱讀",rows.single{it.title=="稍後閱讀"}.folder)
        ui.onNode(hasText("閱讀",substring=false) and hasAnyAncestor(hasTestTag("library-screen"))).performClick()
        ui.onNode(hasText("科技",substring=false) and hasAnyAncestor(hasTestTag("library-screen"))).performClick()
        val matches=ui.onAllNodesWithText("山與海 & 科技",substring=false)
        matches[matches.fetchSemanticsNodes().lastIndex].performScrollTo().assertIsDisplayed()
        screenshot("11-imported-bookmarks")
        val ids=c.store.bookmarkStore.visible().map{it.id}.toSet()
        openImport();pick(filename)
        assertEquals(ids,c.store.bookmarkStore.visible().map{it.id}.toSet())
        openImport();device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.pkg(ui.activity.packageName)),10000))
        assertEquals(ids,c.store.bookmarkStore.visible().map{it.id}.toSet())
        // Remove only this test's local fixtures; no Google account is connected to this QA package.
        ui.runOnIdle{rows.forEach{c.store.bookmarkStore.remove(it.id)}}
        assertEquals(before,c.store.bookmarkStore.visible().map{it.id}.toSet())
        ui.activity.contentResolver.delete(uri,null,null)
    }
}
