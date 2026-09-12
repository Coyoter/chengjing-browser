package tw.techtarian.browser

import android.view.MotionEvent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LibraryJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+18000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(120)};assertTrue("Condition timed out",check())}
    private fun open(url:String){main{c.newTab(url)};until{c.active!!.url==url&&eval("document.readyState")=="\"complete\""};ui.waitForIdle();until{eval("innerWidth").toDouble()>0}}
    private fun libraryText(text:String)=ui.onNode(hasText(text,substring=false) and hasAnyAncestor(hasTestTag("library-screen")))
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(500);val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.5-QA")};val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Before fun isolated(){org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun foldersStartCollapsedAndSearchAcrossTwoThousandBookmarks(){
        val records=(0 until 2000).map{i->val folder=when{ i<1200->"書籤列 / 小說 / 分類${i%8}";i<1500->"行動版書籤 / 生活";else->"其他書籤 / 工具"};Bookmark(Bookmark.id("https://example.com/library-$i",folder),"https://example.com/library-$i","QA 書籤 $i",folder,1)}
        main{c.store.bookmarkStore.importRows(records);c.revision++;c.sheet="bookmarks"}
        libraryText("書籤列").assertIsDisplayed();libraryText("行動版書籤").assertIsDisplayed();libraryText("其他書籤").assertIsDisplayed()
        ui.onAllNodes(hasText("QA 書籤",substring=true) and hasAnyAncestor(hasTestTag("library-screen"))).assertCountEquals(0)
        shot("01-bookmark-folders")
        libraryText("書籤列").performClick();libraryText("小說").performClick();libraryText("分類0").performClick()
        ui.onNodeWithTag("bookmark-list").performScrollToNode(hasText("QA 書籤 800"))
        libraryText("QA 書籤 800").assertIsDisplayed();shot("02-bookmark-folder")
        ui.onNodeWithContentDescription("回上一層").performClick();libraryText("分類1").assertExists()
        ui.onNodeWithTag("library-search").performTextInput("QA 書籤 1999")
        ui.onNode(hasText("QA 書籤 1999") and hasAnyAncestor(hasTestTag("bookmark-list"))).assertIsDisplayed();shot("03-bookmark-search")
        assertEquals(2000,c.store.bookmarkStore.visible().size)
    }
    @Test fun favoritesStaySeparateAndUpdateTheSameReadingEntry(){
        open("https://practice.chengjing.invalid/")
        main{c.store.bookmarkStore.add(c.active!!.url,"長期書籤","閱讀")}
        val before=c.store.bookmarkStore.all().map{it.json().toString()}
        eval("scrollTo(0,200)");until{eval("scrollY").toDouble()>100}
        val done=CountDownLatch(1);main{ui.activity.lifecycleScope.launch{c.saveFavorite();done.countDown()}};assertTrue(done.await(8,TimeUnit.SECONDS))
        val f=c.favorites.all().single();assertTrue(f.scrollY>100)
        main{c.favorites.rename(f.id,"我的小說");c.revision++;c.sheet="favorites"}
        libraryText("我的小說").assertIsDisplayed();shot("04-favorites")
        val previousDocument=eval("performance.timeOrigin")
        libraryText("我的小說").performClick();ui.waitForIdle()
        until{eval("performance.timeOrigin")!=previousDocument&&eval("document.readyState")=="\"complete\""&&eval("scrollY").toDouble()>100}
        assertEquals(f.id,c.active!!.favoriteId)
        eval("document.querySelector('#next-page').scrollIntoView({block:'center'})")
        ui.waitForIdle()
        val point=JSONObject(eval("(()=>{const r=document.querySelector('#next-page').getBoundingClientRect();return {x:(r.x+r.width/2)*devicePixelRatio,y:(r.y+r.height/2)*devicePixelRatio}})()"))
        main{val t=SystemClock.uptimeMillis();val x=point.getDouble("x").toFloat();val y=point.getDouble("y").toFloat();c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0));c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,x,y,0))}
        until{c.active!!.url.contains("/second")&&eval("document.readyState")=="\"complete\""}
        eval("scrollTo(0,150)")
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithText("更新收藏進度",substring=false).performScrollTo().performClick()
        until{c.favorites.get(f.id)?.url?.contains("/second")==true}
        assertEquals(1,c.favorites.all().size);assertEquals("我的小說",c.favorites.get(f.id)!!.title)
        assertEquals(before,c.store.bookmarkStore.all().map{it.json().toString()})
        val persisted=FavoriteStore(ui.activity).get(f.id)!!;assertTrue(persisted.url.contains("/second"))
        ui.activityRule.scenario.recreate()
        until{c.active!!.url.contains("/second")&&c.active!!.favoriteId==f.id}
        main{c.sheet="favorites"};shot("05-updated-reading")
        main{c.removeFavorite(f.id)}
        assertEquals(before,c.store.bookmarkStore.all().map{it.json().toString()})
    }
    @Test fun firstAddressTapSelectsAllAndTypingReplacesIt(){
        open("https://example.com/")
        val address=ui.onNodeWithTag("address-input")
        address.performTouchInput{click()}
        ui.waitForIdle()
        val range=address.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0,"https://example.com/".length),range)
        shot("06-address-selected")
        address.performTextInput("澄境搜尋測試")
        address.assertTextEquals("澄境搜尋測試")
        address.performTouchInput{click(centerLeft)}
        ui.waitForIdle()
        assertTrue(address.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
        address.performImeAction()
        until{c.active!!.url.startsWith("https://www.google.com/search?")}
    }
}
