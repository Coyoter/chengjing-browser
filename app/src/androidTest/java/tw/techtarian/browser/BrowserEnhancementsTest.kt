package tw.techtarian.browser
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors

class BrowserEnhancementsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    @Before fun isolated(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"));main{c.sheet="";c.stopEye();c.tabs.toList().drop(1).forEach{c.closeTab(it.id)}}}
    @Test fun fullscreenWindowHidesSystemBarsAndRestoresOnBack(){
        var callbacks=0
        main{c.active!!.web.webChromeClient!!.onShowCustomView(FrameLayout(ui.activity),object:WebChromeClient.CustomViewCallback{override fun onCustomViewHidden(){callbacks++}})}
        ui.onNodeWithTag("video-fullscreen").assertIsDisplayed()
        ui.waitUntil(15000){val view=c.fullScreenView;view!=null&&ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.systemBars())==false}
        ui.waitUntil(10000){c.fullScreenView?.hasWindowFocus()==true}
        // UiAutomator 2.3 key injection returns false on this API 36 image.
        // Send an actual Android input event through the instrumentation shell instead.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
        ).use{it.readBytes()}
        ui.waitUntil(10000){c.fullScreenView==null};assertEquals(1,callbacks)
        ui.waitUntil(10000){ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars())==true}
    }
    @Test fun addressHistoryShowsMatchingPagesAndClearsSearchTerms(){
        main{c.store.clearHistory();c.store.visit("https://example.com/reading","澄境閱讀測試");c.store.recordSearch("澄境筆記",Domains.address("澄境筆記"));c.newTab();c.revision++}
        ui.onNodeWithTag("address-input").performClick().performTextReplacement("澄境")
        ui.onNodeWithTag("address-suggestions").assertIsDisplayed()
        ui.onNodeWithText("澄境閱讀測試").assertExists();ui.onNodeWithText("澄境筆記").assertExists()
        main{c.store.clearHistory();c.revision++}
        ui.onNodeWithTag("address-suggestions").assertDoesNotExist()
        assertTrue(c.store.searches().isEmpty())
    }
    @Test fun faviconLoadsWithoutLeakingBookmarkPathOrCredentials(){
        val icon=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
        val bytes=ByteArrayOutputStream().also{icon.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        val server=ServerSocket(0);val pool=Executors.newSingleThreadExecutor()
        val request=pool.submit<List<String>>{server.accept().use{s->val reader=s.getInputStream().bufferedReader();val lines=mutableListOf<String>();while(true){val line=reader.readLine();if(line.isNullOrBlank())break;lines.add(line)};s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()+bytes);lines}}
        try{
            val loaded=runBlocking{c.icons.load("http://reader:secret@127.0.0.1:${server.localPort}/private-chapter?token=hidden")}
            assertNotNull(loaded);val lines=request.get();assertEquals("GET /favicon.ico HTTP/1.1",lines.first());assertFalse(lines.any{it.contains("secret")||it.contains("hidden")||it.startsWith("Authorization",true)})
        }finally{server.close();pool.shutdownNow()}
    }
    @Test fun faviconIsPresentInBookmarkAndFavoriteRows(){
        main{
            val icon=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
            c.icons.remember("https://example.com/",icon)
            c.store.bookmarkStore.visible().forEach{c.store.bookmarkStore.remove(it.id)}
            c.store.bookmarkStore.add("https://example.com/book","圖示測試書籤","")
            c.sheet="bookmarks";c.revision++
        }
        ui.onNodeWithText("未分類",useUnmergedTree=true).performClick()
        ui.onNodeWithText("圖示測試書籤").assertExists();ui.onAllNodesWithTag("site-icon").assertCountEquals(1)
        main{c.favorites.all().forEach{c.favorites.remove(it.id)};c.favorites.save("https://example.com/chapter","圖示測試收藏",0.0,0.0,null,true);c.sheet="favorites";c.revision++}
        ui.waitForIdle();Thread.sleep(600)
        val shot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"favorite-icon-diagnostic.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-1.2.0-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}
        android.util.Log.i("CJPolish","Icon bounds: "+ui.onNode(hasTestTag("site-icon") and hasAnyAncestor(hasTestTag("library-screen")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot)
        ui.onNode(hasText("圖示測試收藏") and hasAnyAncestor(hasTestTag("library-screen"))).assertIsDisplayed();ui.onNode(hasTestTag("site-icon") and hasAnyAncestor(hasTestTag("library-screen")),useUnmergedTree=true).assertIsDisplayed()
    }
}
