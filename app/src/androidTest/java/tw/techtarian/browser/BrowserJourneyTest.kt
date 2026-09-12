package tw.techtarian.browser
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class BrowserJourneyTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun eval(script:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(timeout:Long=12000,check:()->Boolean){val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue("Condition timed out",check())}
    private fun ready(){until{eval("typeof window.__chengjingEye")=="\"object\""&&eval("document.readyState")=="\"complete\""}}
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(350);val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-QA-"+BuildConfig.BUILD_TYPE)};val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    private fun reloadReady(action:()->Unit){val old=eval("performance.timeOrigin");main(action);until{eval("performance.timeOrigin")!=old};ready()}
    private fun tap(selector:String){val j=JSONObject(eval("(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"));main{val web=c.active!!.web;val x=(j.getDouble("x")*j.getDouble("dpr")).toFloat();val y=(j.getDouble("y")*j.getDouble("dpr")).toFloat();val t=SystemClock.uptimeMillis();web.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0));web.dispatchTouchEvent(MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,x,y,0))}}
    @Before fun clean(){main{c.store.save(SiteRules(Domains.scope("https://practice.chengjing.invalid/")));c.exceptions.clear();c.refreshScripts()}}
    @Test fun selectionPersistenceException(){
        main{c.newTab()};shot("01-home-light")
        ui.onNodeWithText("進入練習場").performScrollTo().performClick();ready();shot("02-practice-before")
        ui.onNodeWithText("天眼",useUnmergedTree=true).performClick();until{eval("window.__chengjingEye.status().enabled")=="true"};assertEquals("",c.sheet);shot("03-outlines")
        tap("#sponsor-overlay");until{c.selection!=null}
        while(c.selection!!.selector!="#sponsor-overlay"){val old=c.selection!!.selector;ui.onNodeWithText("選取外面一層").performScrollTo().performClick();until{c.selection?.selector!=old}}
        shot("04-selected")
        ui.onNodeWithText("預覽移除").performScrollTo().performClick();until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")=="\"none\""};shot("05-preview")
        ui.onNodeWithText("儲存",useUnmergedTree=true).performClick();until{c.site.rules.isNotEmpty()}
        reloadReady{c.navigate("https://practice.chengjing.invalid/second")};until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")=="\"none\""};assertEquals("\"換了內容的元件，仍然是相同結構。\"",eval("document.querySelector('#ad-copy').textContent"));shot("06-next-page")
        reloadReady{c.exception()};until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")!="\"none\""};shot("07-exception")
        reloadReady{c.exception()};until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")=="\"none\""};assertEquals(1,BrowserStore(ui.activity).get(c.domain).rules.size)
        reloadReady{c.reload()};until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")=="\"none\""}
        reloadReady{c.restoreRules(c.domain)};until{eval("getComputedStyle(document.querySelector('#sponsor-overlay')).display")!="\"none\""}
    }
    @Test fun dynamicShadowPrivacyAndCode(){
        reloadReady{c.navigate("https://practice.chengjing.invalid/")}
        eval("document.body.insertAdjacentHTML('beforeend','<div id=shadow-host></div><input id=private-field value=TOP_SECRET>');document.querySelector('#shadow-host').attachShadow({mode:'open'}).innerHTML='<div id=shadow-promo>SHADOW</div>'")
        main{c.beginEye()};eval("window.__chengjingEye.select('#shadow-host >>> #shadow-promo')");until{c.selection!=null}
        val latch=CountDownLatch(1);main{ui.activity.lifecycleScope.launch{c.removeSelected();latch.countDown()}};assertTrue(latch.await(8,TimeUnit.SECONDS))
        until{eval("getComputedStyle(document.querySelector('#shadow-host').shadowRoot.querySelector('#shadow-promo')).display")=="\"none\""}
        main{c.stopEye()};until{eval("getComputedStyle(document.querySelector('#shadow-host').shadowRoot.querySelector('#shadow-promo')).display")!="\"none\""};assertFalse(eval("window.__chengjingEye.snapshot()").contains("TOP_SECRET"))
        main{c.saveSite(c.site.copy(rules=listOf(ElementRule("#late-promo"))))};until{eval("getComputedStyle(document.querySelector('#late-promo')).display")=="\"none\""}
        eval("document.querySelector('#late-promo').remove();let n=document.createElement('div');n.id='late-promo';n.textContent='DIFFERENT CONTENT';document.body.append(n)");until{eval("getComputedStyle(document.querySelector('#late-promo')).display")=="\"none\""}
        main{c.saveSite(c.site.copy(css="h1{color:rgb(255, 0, 0)!important}",js="document.body.dataset.custom='ran'"),true)};ready();until{eval("document.body.dataset.custom")=="\"ran\""};assertEquals("\"rgb(255, 0, 0)\"",eval("getComputedStyle(document.querySelector('h1')).color"))
        reloadReady{c.exception()};assertEquals("null",eval("document.body.dataset.custom"))
    }
    @Test fun themesBookmarksAndPublicWebsite(){
        main{c.newTab();c.sheet="settings"};ui.onNodeWithText("深色",useUnmergedTree=true).performClick();shot("08-settings-dark");main{c.sheet=""};shot("09-home-dark")
        main{c.sheet="settings"};ui.onNodeWithText("淺色",useUnmergedTree=true).performClick()
        val rows=BookmarkFormat.parseHtml("<DL><DT><H3>閱讀</H3><DL><DT><A HREF='https://example.com/'>測試書籤</A></DL></DL>");main{c.store.bookmarkStore.importRows(rows);c.sheet="bookmarks"};ui.onAllNodesWithText("測試書籤")[0].assertExists();shot("10-bookmarks")
        main{c.sheet="sync"};ui.onNodeWithText("使用 Google 帳戶連結").assertExists();shot("11-google-sync")
        reloadReady{c.sheet="";c.navigate("https://example.com/")};assertEquals("\"Example Domain\"",eval("document.querySelector('h1').textContent"));shot("12-public-website")
    }
    @Test fun popupGuardEncryptionAndModelCatalog(){
        reloadReady{c.navigate("https://practice.chengjing.invalid/")}
        main{c.saveSite(c.site.copy(guard=true))}
        val before=c.blockedCount
        eval("window.open('https://example.com/')")
        until{c.blockedCount>before}
        eval("location.href='https://example.com/?autoredirect'")
        until{c.active!!.blockedUrl.isNotEmpty()}
        assertTrue(c.active!!.url.contains("practice.chengjing.invalid"))
        assertEquals("\"undefined\"",eval("typeof window.ChengJingSelection.saveKey"))
        val store=c.store
        main{store.saveKey("sk-test-chengjing-only")}
        assertEquals("sk-test-chengjing-only",store.readKey())
        assertFalse(ui.activity.getSharedPreferences("browser-v1",0).getString("api-key","")!!.contains("sk-test"))
        main{store.saveKey("")}
        val models=runBlocking{OpenRouter().models()}
        assertEquals(3,models.size)
        assertTrue(models.any{it.startsWith("deepseek/")})
        assertTrue(models.any{it.startsWith("google/gemini-")})
        assertTrue(models.any{it.startsWith("openai/gpt-")})
    }

}
