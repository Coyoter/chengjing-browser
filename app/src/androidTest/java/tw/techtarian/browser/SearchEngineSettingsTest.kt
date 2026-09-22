package tw.techtarian.browser

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class SearchEngineSettingsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val store get()=c.store
    @Before fun before(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();ui.activity.applyAppearance("light");store.useSearchEngine("google");store.clearHistory();c.sheet=""}
    }
    @After fun after(){ui.runOnIdle{store.useSearchEngine("google");store.clearHistory();c.sheet="";c.closePrivateTabs();ui.activity.applyAppearance("system")}}
    private fun open(){
        ui.runOnIdle{c.sheet="settings"}
        ui.onNodeWithText("瀏覽",useUnmergedTree=true).performClick()
        ui.onNodeWithTag("menu-row:預設搜尋引擎").performScrollTo().performClick()
        ui.onNodeWithTag("search-engine-summary").assertIsDisplayed()
    }
    private fun screenshot(name:String){
        ui.waitForIdle();val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use{it.readBytes()}
        shell("mkdir -p /data/local/tmp/search-engine");shell("screencap -p /data/local/tmp/search-engine/$name.png")
    }
    @Test fun browsingSettingsShowAllRequestedChoicesAndGoogleIsDefault(){
        open()
        listOf("google","bing","yahoo","baidu","naver","wiki","custom").forEach{ui.onNodeWithTag("search-engine:$it").performScrollTo().assertIsDisplayed()}
        ui.onNodeWithTag("search-engine:google").assertIsSelected()
        ui.onNodeWithTag("search-engine-summary").performScrollTo();screenshot("01-default-google-light")
    }
    @Test fun selectingBingChangesAddressSearchButNotDirectUrlsAndRecordsTheQuery(){
        open();ui.onNodeWithTag("search-engine:bing").performScrollTo().performClick()
        ui.runOnIdle{
            assertEquals("bing",store.searchEngine)
            assertEquals("https://www.bing.com/search?q=%E6%BE%84%E5%A2%83%20AI",Domains.address("澄境 AI",store.searchSettings))
            assertEquals("https://techtarian.com/",Domains.address("techtarian.com",store.searchSettings))
            c.newTab();c.navigate("澄境 AI")
        }
        ui.waitUntil(10000){c.active?.url?.startsWith("https://www.bing.com/search?q=")==true||c.active?.pendingUrl?.startsWith("https://www.bing.com/search?q=")==true}
        ui.runOnIdle{assertEquals(listOf("澄境 AI"),store.searches())}
    }
    @Test fun customTemplateValidatesLiveSavesAndPersistsAcrossActivityRecreation(){
        ui.runOnIdle{ui.activity.applyAppearance("dark")}
        open();ui.onNodeWithTag("search-engine:custom").performScrollTo().performClick()
        val input=ui.onNodeWithTag("custom-search-template").performScrollTo()
        input.performTextReplacement("http://find.example/search?q={query}")
        ui.waitForIdle();ui.onNodeWithTag("custom-search-guidance",useUnmergedTree=true).assertTextContains("為保護搜尋內容，只能使用 HTTPS")
        ui.onNodeWithTag("save-custom-search").assertIsNotEnabled()
        input.performTextReplacement("https://find.example/search?lang=zh-TW&q={query}")
        ui.onNodeWithTag("custom-search-preview").assertExists()
        ui.runOnIdle{assertEquals("https://find.example/search?lang=zh-TW&q=%E6%BE%84%E5%A2%83%20%E7%80%8F%E8%A6%BD%E5%99%A8",SearchEngines.searchUrl("澄境 瀏覽器",SearchSettings("custom","https://find.example/search?lang=zh-TW&q={query}")))}
        ui.onNodeWithTag("save-custom-search").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        ui.runOnIdle{assertEquals("custom",store.searchEngine);assertEquals("find.example",SearchEngines.description(store.searchSettings))}
        screenshot("02-custom-dark-ready")
        ui.activityRule.scenario.recreate()
        ui.runOnIdle{
            assertEquals("custom",c.store.searchEngine)
            assertEquals("https://find.example/search?lang=zh-TW&q=private%20terms",Domains.address("private terms",c.store.searchSettings))
        }
    }
    @Test fun privateSearchUsesTheChoiceWithoutSavingItsWordsToHistory(){
        ui.runOnIdle{store.useSearchEngine("wiki");val tab=c.newTab(incognito=true)!!;c.navigate("不留下的查詢");assertTrue(tab.pendingUrl.startsWith("https://zh.wikipedia.org/"))}
        ui.runOnIdle{assertTrue(store.searches().isEmpty())}
    }
}
