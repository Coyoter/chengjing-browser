package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FavoriteAppearanceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue("State did not update",check())}
    private fun state(value:String){ui.onNodeWithTag("quick-favorite").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,value))}
    private fun save(name:String,bitmap:Bitmap){
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.8-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(400);save(name,InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())}
    @Before fun isolated(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun starFollowsSavedPageAcrossReopenChaptersHistoryAndRemoval(){
        val url="https://practice.chengjing.invalid/favorite-state"
        main{c.favorites.all().forEach{c.removeFavorite(it.id)};c.newTab(url)}
        until{c.active!!.url==url&&eval("document.readyState")=="\"complete\""};ui.waitForIdle()
        state("尚未收藏");shot("01-unsaved-star")
        ui.onNodeWithTag("quick-favorite").performClick();until{c.favorites.forPage(url)!=null};ui.waitForIdle()
        state("已收藏");shot("02-saved-star")
        val id=c.active!!.favoriteId!!
        main{c.newTab(url)};until{c.active!!.url==url&&eval("document.readyState")=="\"complete\""};ui.waitForIdle()
        assertNull(c.active!!.favoriteId);state("已收藏")
        ui.activityRule.scenario.recreate();ui.waitForIdle();until{c.active!!.url==url};state("已收藏")
        val previousDocument=eval("performance.timeOrigin")
        main{c.openFavorite(c.favorites.get(id)!!)};until{eval("performance.timeOrigin")!=previousDocument&&eval("location.href")=="\"$url\""&&eval("document.readyState")=="\"complete\""}
        // Same-document navigation must update the URL and star too.
        eval("""document.body.insertAdjacentHTML('beforeend', '<button id=chapter-button style="position:fixed;top:0;left:0;z-index:2147483647;padding:20px">下一章</button>');document.querySelector('#chapter-button').onclick=()=>history.pushState({},'', '/favorite-next')""")
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("下一章")),5000))
        device.findObject(androidx.test.uiautomator.By.text("下一章")).click()
        until{c.active!!.url.endsWith("/favorite-next")};ui.waitForIdle();state("尚未收藏")
        assertEquals(id,c.active!!.favoriteId)
        ui.onNodeWithTag("quick-favorite").performClick();until{c.favorites.get(id)!!.url.endsWith("/favorite-next")};ui.waitForIdle();state("已收藏")
        assertEquals(1,c.favorites.all().size)
        main{c.active!!.web.goBack()};until{c.active!!.url==url};ui.waitForIdle();state("尚未收藏")
        main{c.active!!.web.goForward()};until{c.active!!.url.endsWith("/favorite-next")};ui.waitForIdle();state("已收藏")
        main{c.removeFavorite(id)};ui.waitForIdle();state("尚未收藏")
    }
    @Test fun menuHasNoHandleAndTitleClearsSystemCutout(){
        main{c.newTab("https://practice.chengjing.invalid/")}
        until{c.active!!.url.startsWith("https://practice.chengjing.invalid")};ui.waitForIdle()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick();ui.waitForIdle()
        ui.onNodeWithTag("sheet-drag-handle").assertDoesNotExist()
        val title=ui.onNodeWithText("澄境瀏覽器",substring=false).fetchSemanticsNode().boundsInWindow
        val insets=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
        assertTrue("Menu title must clear the system/camera area",title.top>=insets.top)
        shot("03-menu-with-cutout")
        main{c.sheet=""};ui.waitForIdle()
        val icon=ui.activity.packageManager.getApplicationIcon(ui.activity.packageName)
        val bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,432,432);icon.draw(Canvas(bitmap));save("04-installed-icon",bitmap)
        val brand=ui.activity.getDrawable(R.drawable.ic_launcher)!!
        val brandBitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);brand.setBounds(0,0,432,432);brand.draw(Canvas(brandBitmap));save("05-home-brand",brandBitmap)
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome();Thread.sleep(500);device.pressRecentApps();Thread.sleep(1000)
        val hierarchy=java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(hierarchy)
        assertTrue("Recent apps must be visible",hierarchy.toString().contains("overview_panel"))
        save("06-recent-apps",InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());device.pressBack()
    }
}
