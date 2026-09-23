package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuleEditingJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val url="https://practice.chengjing.invalid/"
    private val domain=Domains.scope(url)
    private val first=PageEdit("editable","#notice-banner",css="color: green;",html="<b id='saved-note'>先前規則</b>")
    private val second=PageEdit("keep","#reading-card",html="<small id='keep-note'>保留另一項</small>")
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String):String {val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(condition:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(100)};assertTrue("Timed out; ${c.notice}",condition())}
    private fun screenshot(name:String){
        ui.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/rule-editing")).use{it.readBytes()}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /data/local/tmp/rule-editing/$name.png")).use{it.readBytes()}
    }
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        main{
            ui.activity.bookmarkSync.disconnect()
            // Each case owns only its practice page, not restored tabs from preceding suites.
            c.closePrivateTabs();c.closeAllTabs(incognito=false)
            ui.activity.getSharedPreferences("browser-v1",0).edit().remove("site:practice.chengjing.invalid").commit()
            c.exceptions.clear();c.stopEye();c.sheet="";c.store.aiProvider="openrouter";c.store.saveKey("qa-not-a-real-key")
            c.saveSite(SiteRules(domain,css="h1 {letter-spacing: 1px}",edits=listOf(first,second)));c.navigate(url)
        }
        until{eval("document.querySelector('#saved-note')?.textContent")=="\"先前規則\""};ui.waitForIdle()
    }
    @After fun cleanup(){main{c.store.saveKey("");c.sheet="";c.stopEye();c.saveSite(SiteRules(domain));ui.activity.applyAppearance("system")}}
    private fun openFirst(){main{c.sheet="rules"}
        ui.onNodeWithText(first.selector).performScrollTo().performClick();ui.onNodeWithTag("saved-rule-css").assertExists()
    }
    private fun analyze(){
        ui.onNodeWithText("請 AI 修改這項規則").performScrollTo().performClick()
        ui.onNodeWithTag("developer-ai-problem").performTextReplacement("改成藍色，保留原本內容")
        // Wait for the real dialog IME to close before tapping rows whose bounds move.
        if(android.os.Build.VERSION.SDK_INT>=30){
            main{android.view.inspector.WindowInspector.getGlobalWindowViews().forEach{it.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())}}
            ui.waitUntil(5000){var hidden=false;main{hidden=android.view.inspector.WindowInspector.getGlobalWindowViews().none{it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true}};hidden}
        }
        ui.waitForIdle()
        ui.onNode(isToggleable()).performScrollTo().assertIsOff().performClick().assertIsOn()
        ui.onNodeWithText("傳送並取得建議").performScrollTo().assertIsEnabled().performClick()
        try {
            ui.waitUntil(10000){ui.onAllNodesWithText("更新原規則並套用").fetchSemanticsNodes().isNotEmpty()||ui.onAllNodesWithTag("developer-error").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("更新原規則並套用").assertExists()
        }catch(failure:Throwable){throw AssertionError("AI revision did not produce a proposal; sheet=${c.sheet}, tabs=${c.tabs.size}, notice=${c.notice}\n"+ui.onRoot().printToString(),failure)}
    }
    @Test fun clickOpensEditorAndManualSaveUpdatesInPlace(){
        openFirst();assertEquals(2,c.site.edits.size)
        screenshot("editor-light")
        ui.onNodeWithTag("saved-rule-html").performScrollTo().assertTextContains(first.html).performTextReplacement("<b id='saved-note'>手動修訂</b>")
        ui.onNodeWithTag("save-rule").performScrollTo().performClick()
        until{eval("document.querySelector('#saved-note')?.textContent")=="\"手動修訂\""}
        assertEquals(listOf("editable","keep"),c.site.edits.map{it.id});assertEquals(second,c.site.edits[1]);assertEquals(first.css,c.site.edits[0].css)
        ui.activityRule.scenario.recreate();until{eval("document.querySelector('#saved-note')?.textContent")=="\"手動修訂\""}
    }
    @Test fun revisionReadsOriginalSourceAndEditedRuleThenRequiresExplicitApply(){
        var received=false
        main{ui.activity.applyAppearance("dark");c.developerRevision={problem,source,current,id->
            assertEquals("editable",id);assertEquals("改成藍色，保留原本內容",problem)
            val html=JSONObject(source).getString("html")
            assertTrue(html.contains("一般資訊"));assertFalse(html.contains("saved-note"));assertFalse(html.contains("data-cj-removed"))
            assertEquals(first.html,current.edits[0].html);assertEquals("color: purple;",current.edits[0].css)
            received=true;RuleRevision.parse("""{"explanation":"保留 HTML，更新顏色","css":"color: rgb(0, 0, 255);"}""",current,id)
        }}
        openFirst();ui.onNodeWithTag("saved-rule-css").performTextReplacement("color: purple;")
        analyze();assertTrue(received);assertEquals(first,c.site.edits[0])
        ui.onNodeWithText("更新原規則並套用").performScrollTo();screenshot("ai-revision-dark")
        ui.onNodeWithText("更新原規則並套用").performScrollTo().performClick()
        until{eval("getComputedStyle(document.querySelector('#notice-banner')).color")=="\"rgb(0, 0, 255)\""}
        assertEquals(listOf("editable","keep"),c.site.edits.map{it.id});assertEquals(first.html,c.site.edits[0].html);assertEquals(second,c.site.edits[1])
    }
    @Test fun cancelDeletionKeepsRuleAndConfirmedDeletionPreservesOthers(){
        openFirst();ui.onNodeWithText("刪除這項規則").performScrollTo().performClick();assertEquals(2,c.site.edits.size)
        ui.onNodeWithText("取消").performClick();assertEquals(2,c.site.edits.size)
        ui.onNodeWithText("刪除這項規則").performScrollTo().performClick();ui.onNodeWithText("刪除",substring=false).performClick()
        until{eval("document.querySelector('#saved-note')")=="null"};assertEquals(listOf(second),c.site.edits)
    }
    @Test fun changedDocumentCannotApplyAnOldAiProposal(){
        main{c.developerRevision={_,_,current,id->RuleRevision.parse("""{"css":"color: blue;"}""",current,id)}}
        openFirst();analyze();val before=eval("performance.timeOrigin")
        main{c.active!!.web.reload()};until{eval("performance.timeOrigin")!=before&&eval("!!document.querySelector('#saved-note')")=="true"}
        ui.onNodeWithText("更新原規則並套用").performScrollTo().performClick()
        ui.onNodeWithTag("developer-error").performScrollTo().assertTextContains("網頁已變更，請重新分析")
        assertEquals(first,c.site.edits[0])
    }
    @Test fun manualEditingDoesNotRequireTheSavedElementOnThisPage(){
        eval("document.querySelector('#notice-banner').remove()")
        openFirst();ui.onNodeWithTag("saved-rule-css").performTextReplacement("color: blue;")
        ui.onNodeWithTag("save-rule").performScrollTo().performClick()
        until{c.site.edits[0].css=="color: blue;"};assertEquals(second,c.site.edits[1])
    }
    @Test fun domainsOpenSavedRulesAndWebsiteRevisionPreservesElementEdits(){
        main{c.sheet="domains"};ui.onNodeWithTag("menu-row:$domain").performScrollTo().performClick()
        ui.onNodeWithText("網站 CSS / JS / HTML").performScrollTo().performClick()
        main{c.developerRevision={_,source,current,id->assertNull(id);assertTrue(JSONObject(source).getString("html").contains("reading-card"));RuleRevision.parse("""{"css":"h1 {color: blue}"}""",current,id)}}
        analyze();ui.onNodeWithText("更新原規則並套用").performScrollTo().performClick()
        until{c.site.css=="h1 {color: blue}"};assertEquals(listOf(first,second),c.site.edits)
    }
    @Test fun changedSavedRulesAreProtectedEvenWhenThePreviewDraftIsUnchanged(){
        main{c.beginEye();c.developerRevision={_,_,current,id->RuleRevision.parse("""{"css":"color: blue;"}""",current,id)}}
        openFirst();analyze()
        main{c.store.save(c.site.copy(css="h1 {font-size: 28px}"))}
        ui.onNodeWithText("更新原規則並套用").performScrollTo().performClick()
        ui.onNodeWithTag("developer-error").performScrollTo().assertTextContains("規則已由其他操作更新，請重新開啟")
        assertEquals("h1 {font-size: 28px}",c.site.css);assertEquals(first,c.site.edits[0])
    }
    @Test fun originalHtmlOmitsFormContentsAndBrowserCode(){
        val before=eval("performance.timeOrigin")
        main{c.active!!.web.loadDataWithBaseURL(url,"""<html><body><main id='notice-banner'>原始文字<input value='secret-input'><textarea>secret-area</textarea><div contenteditable='true'>secret-editable</div><script>var token='secret-script';</script><a href='https://example.com/read?token=secret-query' onclick='secret-handler()'>連結</a></main><div id='reading-card'>另一項</div></body></html>""","text/html","UTF-8",null)}
        until{eval("performance.timeOrigin")!=before&&eval("!!document.querySelector('#saved-note')")=="true"}
        val source=JSONObject(JSONArray("[${eval("window.__chengjingEye.originalSource('#notice-banner')")}]").getString(0)).getString("html")
        assertTrue(source.contains("原始文字"));assertFalse(source.contains("secret-"));assertFalse(source.contains("saved-note"));assertFalse(source.contains("onclick"))
    }
}
