package tw.techtarian.browser

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Base64
import android.util.TypedValue
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Appearance is tested against opposite system modes, not just two matching screenshots. */
class AppearanceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    @Before fun before(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        device.executeShellCommand("cmd uimode night no")
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.prompts.cancel();c.sheet="";c.stopEye();c.tabs.map{it.id}.forEach{c.closeTab(it)};ui.activity.applyAppearance("light")}
        mode("light",false)
    }
    @After fun after(){
        ui.runOnIdle{c.prompts.cancel();c.imagePreview=null;c.sheet="";c.closePrivateTabs();ui.activity.applyAppearance("system")}
        device.executeShellCommand("cmd uimode night no")
    }
    private fun mode(choice:String,dark:Boolean){
        ui.runOnIdle{ui.activity.applyAppearance(choice)}
        ui.waitUntil(10000){(ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
        ui.runOnIdle{
            val flag=TypedValue();assertTrue(ui.activity.theme.resolveAttribute(android.R.attr.isLightTheme,flag,true))
            assertEquals("Native widgets must resolve the same appearance",!dark,flag.data!=0)
        }
    }
    private fun ready(tag:String){ui.waitUntil(10000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag(tag).assertIsDisplayed()}
    private fun surface(tag:String,dark:Boolean){
        ready(tag)
        val bitmap=ui.onNodeWithTag(tag).captureToImage().toPixelMap()
        val color=bitmap[bitmap.width/2,12.coerceAtMost(bitmap.height-1)].toArgb()
        val light=ColorUtils.calculateLuminance(color)
        assertTrue("Wrong $tag surface for dark=$dark: ${Integer.toHexString(color)}",if(dark)light<.12 else light>.65)
        val expected=ui.activity.getColor(R.color.browser_surface_container_high)
        assertTrue("Surface must use the shared palette, not simply any dark/light color",
            kotlin.math.abs(Color.red(color)-Color.red(expected))<=2&&
            kotlin.math.abs(Color.green(color)-Color.green(expected))<=2&&
            kotlin.math.abs(Color.blue(color)-Color.blue(expected))<=2)
    }
    private fun shot(name:String){
        ui.waitForIdle();device.executeShellCommand("mkdir -p /data/local/tmp/appearance")
        device.executeShellCommand("screencap -p /data/local/tmp/appearance/$name.png")
    }
    private fun eval(code:String):String{
        val latch=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{c.active!!.web.evaluateJavascript(code){result.set(it);latch.countDown()}}
        assertTrue(latch.await(8,TimeUnit.SECONDS));return result.get()
    }
    private fun fixture(private:Boolean=false):BrowserTab{
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
        val data=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle()}.toByteArray()
        val html="""<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='color-scheme' content='light dark'><title>外觀測試頁</title>
            <style>body{margin:24px}img{display:block}article{height:2000px}@media(prefers-color-scheme:dark){body{background:#0f1513;color:#f2efe7}}</style></head>
            <body><img id='picture' alt='澄境圖片選單 · 外觀驗證' src='data:image/png;base64,${Base64.encodeToString(data,Base64.NO_WRAP)}' width='150' height='150'>
            <select aria-label='選擇項目'><option>第一個選項</option><option>第二個選項</option></select><article>外觀測試</article></body></html>"""
        lateinit var tab:BrowserTab
        ui.runOnIdle{tab=c.newTab(incognito=private)!!;tab.web.loadDataWithBaseURL("https://appearance.chengjing.invalid/",html,"text/html","UTF-8",null)}
        ui.waitUntil(10000){eval("!!document.querySelector('#picture')?.complete && document.querySelector('#picture').naturalWidth>0")=="true"}
        ready("web-content");return tab
    }
    private fun imageMenu(){
        val drawn=CountDownLatch(1)
        ui.runOnUiThread{c.active!!.web.postVisualStateCallback(1,object:WebView.VisualStateCallback(){override fun onComplete(id:Long){drawn.countDown()}})}
        assertTrue(drawn.await(8,TimeUnit.SECONDS));ui.waitForIdle()
        val p=JSONObject(eval("(()=>{const r=document.querySelector('#picture').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val origin=IntArray(2);ui.runOnUiThread{c.active!!.web.getLocationOnScreen(origin)}
        val x=origin[0]+(p.getDouble("x")*p.getDouble("dpr")).toFloat();val y=origin[1]+(p.getDouble("y")*p.getDouble("dpr")).toFloat()
        val start=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);try{instrumentation.sendPointerSync(e)}finally{e.recycle()}
            if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(850)
        }
        ready("page-context-menu")
    }
    @Test fun imageMenuFollowsAppChoiceAcrossOppositeSystemThemes(){
        fixture()
        for(systemDark in listOf(false,true)){
            device.executeShellCommand("cmd uimode night ${if(systemDark)"yes"else"no"}")
            for(choice in listOf("dark","light","system")){
                val dark=choice=="dark"||(choice=="system"&&systemDark)
                mode(choice,dark);imageMenu();surface("page-context-menu",dark)
                ui.onNodeWithTag("context-action:下載圖片").performScrollTo().assertIsDisplayed()
                ui.onNodeWithTag("context-action:分享圖片").performScrollTo().assertIsDisplayed()
                shot("menu-${if(systemDark)"system-dark"else"system-light"}-$choice")
                ui.onNodeWithContentDescription("關閉長按選單").performClick()
            }
        }
    }
    @Test fun changingAppearancePreservesRegularAndPrivatePagesAndUpdatesWebScheme(){
        val activity=ui.activity;val regular=fixture();val private=fixture(true)
        ui.runOnIdle{private.web.scrollTo(0,180)}
        val count=c.tabs.size;val generation=private.navigationGeneration
        mode("dark",true)
        ui.waitUntil(10000){eval("matchMedia('(prefers-color-scheme: dark)').matches")=="true"}
        ui.runOnIdle{
            assertSame(activity,ui.activity);assertTrue(regular in c.tabs);assertTrue(private in c.tabs)
            assertEquals(count,c.tabs.size);assertEquals(private.id,c.activeId);assertTrue(private.web.scrollY>0);assertEquals(generation,private.navigationGeneration)
        }
        mode("light",false)
        ui.waitUntil(10000){eval("matchMedia('(prefers-color-scheme: dark)').matches")=="false"}
        ui.runOnIdle{assertSame(activity,ui.activity);assertEquals(count,c.tabs.size);assertTrue(private in c.tabs)}
    }
    @Test fun websiteDialogsUseAppAppearanceAndReturnActualJavascriptResults(){
        mode("dark",true);fixture()
        eval("setTimeout(()=>{window.answer=confirm('確認外觀與取消行為')},0)")
        surface("browser-confirmation",true);shot("website-confirm-dark")
        ui.onNodeWithText("取消").performClick();ui.waitUntil(5000){eval("window.answer")=="false"}
        eval("setTimeout(()=>{window.answer=prompt('輸入測試','原本內容')},0)")
        ready("website-prompt-input");ui.onNodeWithTag("website-prompt-input").performTextReplacement("修改內容")
        ui.onNodeWithText("確定").performClick();ui.waitUntil(5000){eval("window.answer")=="\"修改內容\""}
        eval("setTimeout(()=>alert('等待中的網站訊息'),0)");ready("browser-confirmation")
        ui.runOnIdle{c.newTab()};ui.waitUntil(5000){c.prompts.current==null}
    }
    @Test fun privateExportAndDownloadConfirmationsUseTheSameDarkSurface(){
        mode("dark",true);fixture(true);imageMenu()
        ui.onNodeWithTag("context-action:下載圖片").performScrollTo().performClick()
        surface("browser-confirmation",true);shot("private-image-confirm-dark")
        ui.onNodeWithText("取消").performClick()
        ui.runOnIdle{val tab=c.active!!;c.downloadFor(tab,"https://example.com/file.pdf",tab.url,"QA",imageOnly=false)}
        surface("browser-confirmation",true);shot("private-file-confirm-dark")
        ui.onNodeWithText("取消").performClick()
    }
    @Test fun incomingWebLinkLeavesOldPanelsAndPromptsBehind(){
        fixture();val old=c.activeId;val activity=ui.activity;val launchIntent=activity.intent
        try{
            ui.runOnIdle{c.sheet="downloads";c.prompts.confirm("測試提示","舊畫面","確定"){} }
            ready("browser-confirmation")
            ui.runOnIdle{instrumentation.callActivityOnNewIntent(activity,android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://practice.chengjing.invalid/")))}
            ui.waitUntil(10000){c.activeId!=old&&c.active?.url=="https://practice.chengjing.invalid/"}
            ui.onNodeWithTag("browser-panel").assertDoesNotExist();ui.onNodeWithTag("browser-confirmation").assertDoesNotExist()
            ready("web-content")
        }finally{
            // ActivityScenario filters lifecycle events by its original launch Intent.
            // Restore its test bookkeeping only after verifying the actual new-intent UI.
            ui.runOnIdle{activity.intent=launchIntent}
        }
    }
    @Test fun nativeWebsiteSelectFollowsDarkAppOnLightSystem(){
        mode("dark",true);fixture()
        val p=JSONObject(eval("(()=>{const r=document.querySelector('select').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val location=IntArray(2);ui.runOnUiThread{c.active!!.web.getLocationOnScreen(location)}
        device.click(location[0]+(p.getDouble("x")*p.getDouble("dpr")).toInt(),location[1]+(p.getDouble("y")*p.getDouble("dpr")).toInt())
        val option=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("第二個選項")),10000)
        assertNotNull(option);device.waitForIdle()
        val bounds=option!!.visibleBounds
        val screen=instrumentation.uiAutomation.takeScreenshot()!!
        val color=screen.getPixel(bounds.right-6,bounds.centerY());screen.recycle()
        assertTrue("Native select must not be a white popup",ColorUtils.calculateLuminance(color)<.2)
        shot("native-select-dark");option.click()
        ui.waitUntil(5000){eval("document.querySelector('select').value")=="\"第二個選項\""}
    }
    @Test fun allMainSurfacesRemainReadableInBothAppAppearances(){
        for(dark in listOf(false,true)){
            mode(if(dark)"dark"else"light",dark)
            ui.runOnIdle{c.newTab(incognito=false)};ready("browser-home");shot("${if(dark)"dark"else"light"}-home")
            fixture()
            for(sheet in listOf("menu","tabs","history","downloads","bookmarks","favorites","settings","privacy","connection","eye","rules","sync","domains")){
                ui.runOnIdle{c.sheet=sheet}
                ready(if(sheet in setOf("bookmarks","favorites"))"library-screen"else"browser-panel")
                shot("${if(dark)"dark"else"light"}-$sheet")
                if(sheet=="settings")for(category in listOf("瀏覽","AI","外觀")){
                    ui.onNodeWithText(category,useUnmergedTree=true).performClick()
                    shot("${if(dark)"dark"else"light"}-settings-${if(category=="AI")"ai"else if(category=="瀏覽")"browsing"else"appearance"}")
                }
                ui.runOnIdle{c.sheet=""}
            }
        }
    }
    @Test fun liveGoogleDarkMenu(){
        val url=InstrumentationRegistry.getArguments().getString("liveGoogleUrl")
        Assume.assumeTrue(!url.isNullOrBlank())
        mode("dark",true)
        ui.runOnIdle{c.newTab(url!!,incognito=false)}
        val selector="img[src*='play-lh.googleusercontent.com']"
        ui.waitUntil(60000){eval("Array.from(document.querySelectorAll(${JSONObject.quote(selector)})).some(i=>i.naturalWidth>0&&i.getBoundingClientRect().width>150)")=="true"}
        eval("Array.from(document.querySelectorAll(${JSONObject.quote(selector)})).find(i=>i.naturalWidth>0&&i.getBoundingClientRect().width>150).id='picture'")
        imageMenu();surface("page-context-menu",true);shot("google-image-menu-dark")
        ui.onNodeWithTag("context-action:預覽圖片").performClick();ready("image-preview-content");shot("google-image-preview-dark")
    }
    @Test fun themeTextAndActionColorsMeetContrastInBothModes(){
        for(dark in listOf(false,true)){
            mode(if(dark)"dark"else"light",dark)
            val r=ui.activity.resources
            for((fg,bg) in listOf(R.color.browser_on_background to R.color.browser_background,
                R.color.browser_on_surface to R.color.browser_surface_container_high,
                R.color.browser_on_surface_variant to R.color.browser_surface_container_high,
                R.color.browser_primary to R.color.browser_surface_container_high,
                R.color.browser_on_error_container to R.color.browser_error_container,
                R.color.browser_inverse_primary to R.color.browser_inverse_surface)){
                assertTrue("Insufficient text contrast in dark=$dark",ColorUtils.calculateContrast(r.getColor(fg,null),r.getColor(bg,null))>=4.5)
            }
        }
    }
}
