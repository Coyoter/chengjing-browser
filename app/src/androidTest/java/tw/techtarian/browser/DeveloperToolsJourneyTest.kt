package tw.techtarian.browser
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeveloperToolsJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(120)};assertTrue("Timed out: ${c.notice}; ${c.active?.error}; sheet=${c.sheet}; site=${c.site.json()}; DOM="+eval("document.querySelector('#notice-banner')?.outerHTML")+"; UI="+ui.onRoot().printToString(),check())}
    private fun open(){main{ui.activity.getSharedPreferences("browser-v1",0).edit().remove("site:practice.chengjing.invalid").commit();c.saveSite(SiteRules(Domains.scope("https://practice.chengjing.invalid/")));c.newTab("https://practice.chengjing.invalid/")};until{eval("document.querySelector('h1')!=null && innerWidth>300")=="true"};ui.waitForIdle();Thread.sleep(300)}
    private fun select(selector:String){main{c.beginEye();c.chooseSelector(selector)};until{c.selection?.selector==selector&&c.sheet=="selection"}}
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(600);val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-1.1.0-Store")};val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Before fun isolated(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun elementCodePersistsAndCanBeRestored(){
        open();select("#notice-banner")
        ui.onNodeWithTag("menu-row:想做什麼嘗試？直接問 AI").assertExists();shot("02-element-actions")
        ui.onNodeWithText("新增自訂 CSS / JS / HTML").performScrollTo().performClick()
        ui.onNodeWithTag("element-css").performTextReplacement("color: rgb(13, 102, 80);")
        ui.onNodeWithTag("element-js").performScrollTo().performTextReplacement("element.dataset.readerNote = 'ready';")
        ui.onNodeWithTag("element-html").performScrollTo().performTextReplacement("<strong id='custom-note'>我的閱讀提示</strong>")
        ui.onNodeWithText("儲存並套用").performScrollTo().performClick()
        until{eval("document.querySelector('#notice-banner')?.dataset.readerNote")=="\"ready\""&&eval("document.querySelectorAll('#custom-note').length")=="1"}
        assertEquals("\"rgb(13, 102, 80)\"",eval("getComputedStyle(document.querySelector('#notice-banner')).color"))
        val before=eval("performance.timeOrigin");main{c.reload()};until{eval("performance.timeOrigin")!=before&&eval("document.querySelectorAll('#custom-note').length")=="1"}
        eval("document.querySelector('#notice-banner').classList.add('changed')");Thread.sleep(300);assertEquals("1",eval("document.querySelectorAll('#custom-note').length"))
        main{c.restoreRules(Domains.scope("https://practice.chengjing.invalid/"))};until{eval("document.querySelectorAll('#custom-note').length")=="0"}
        assertTrue(c.site.edits.isEmpty())
    }
    @Test fun editHtmlIsScopedAndExceptionRestoresOriginal(){
        open();select("#notice-banner")
        ui.onNodeWithText("修改這段代碼").performScrollTo().performClick()
        until{ui.onNodeWithTag("element-html").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.contains("一般資訊")}
        ui.onNodeWithTag("element-html").performTextReplacement("<span id='edited-banner'>我的網站工具</span>")
        ui.onNodeWithText("儲存並套用").performScrollTo().performClick()
        until{eval("document.querySelector('#edited-banner')?.textContent")=="\"我的網站工具\""}
        assertEquals("true",eval("!!document.querySelector('#sample-overlay')"))
        main{c.exception()};until{eval("document.querySelector('#edited-banner')")=="null"}
        main{c.exception()};until{eval("document.querySelector('#edited-banner')?.textContent")=="\"我的網站工具\""}
    }
    @Test fun websiteAndElementAiAreDistinct(){
        open();main{c.beginEye()};ui.onNodeWithText("想做什麼嘗試？直接問 AI").performClick()
        assertNull(c.aiElement);ui.onNodeWithText("目前網站：",substring=true).assertExists();shot("03-website-ai")
        main{c.sheet=""};select("#notice-banner")
        ui.onNodeWithText("我這樣改對嗎？請 AI 檢查").performScrollTo().performClick()
        assertEquals("#notice-banner",c.aiElement?.selector)
        ui.onNodeWithText("所選元件：",substring=true).assertExists();shot("04-element-ai")
        main{c.sheet="";c.stopEye();c.newTab()};shot("01-home")
    }
    @Test fun realProviderResponsesApplyInBothScopesAndUndo(){
        for(kind in listOf("website","element")){
            open()
            val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("ai-$kind-response.json").bufferedReader().use{it.readText()}
            main{c.store.saveKey("qa-placeholder-not-a-real-key");c.developerSuggestion={_,structure,original,selected->
                assertTrue(structure.contains("reading-card"))
                if(kind=="element")assertEquals("#reading-card",selected)else assertNull(selected)
                DeveloperProposals.parse(fixture,original,selected)
            }}
            if(kind=="element")select("#reading-card")else main{c.beginEye()}
            main{c.aiElement=if(kind=="element")c.selection else null;c.sheet="develop-ai"}
            ui.onNodeWithTag("developer-ai-problem").performTextReplacement("新增深綠色閱讀提醒，保留原本內容")
            ui.onNode(isToggleable()).performScrollTo().performClick()
            ui.onNodeWithText("傳送並取得建議").performScrollTo().performClick()
            until{ui.onAllNodesWithText("一鍵套用建議").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("一鍵套用建議").performScrollTo().performClick()
            until{eval("document.querySelector('#reading-card [role=note]')?.textContent")=="\"記得休息一下\""}
            assertEquals(1,c.site.edits.size)
            assertEquals("1",eval("document.querySelectorAll('#reading-card [role=note]').length"))
            main{c.restoreRules(c.domain);c.store.saveKey("")}
            until{eval("document.querySelectorAll('#reading-card [role=note]').length")=="0"}
        }
    }

    @Test fun siteSyncOptionIsExplicitAndPersistent(){
        main{c.store.syncSiteSettings=false;c.sheet="sync"}
        ui.onNodeWithTag("sync-site-settings").performScrollTo().assertIsOff()
        ui.onNodeWithText("開啟後，網域",substring=true).assertExists()
        ui.onNodeWithTag("sync-site-settings").performClick()
        assertTrue(BrowserStore(ui.activity).syncSiteSettings)
        shot("05-google-sync")
        ui.onNodeWithTag("sync-site-settings").performClick()
        assertFalse(BrowserStore(ui.activity).syncSiteSettings)
    }

    @Test fun reconfigurationDoesNotExposeOtherSitesCode(){
        open()
        main{c.store.save(SiteRules("other.example",css="/* private-other-site-marker */"))}
        eval("window.observedArrays=[];window.originalFilter=Array.prototype.filter;Array.prototype.filter=function(...args){window.observedArrays.push(JSON.stringify(this));return window.originalFilter.apply(this,args)}")
        main{c.refreshScripts()}
        Thread.sleep(350)
        assertEquals("false",eval("window.observedArrays.some(x=>x.includes('private-other-site-marker'))"))
        eval("Array.prototype.filter=window.originalFilter")
    }

}
