package tw.techtarian.browser

import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class HomeSettingsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    @Before fun setup(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        f.reset()
        ui.runOnIdle{c.home.updateEnabled(false);c.home.useDefault()}
    }
    @After fun cleanup(){ui.runOnIdle{c.home.updateEnabled(false);c.home.useDefault();c.closePrivateTabs();c.sheet=""}}
    private fun settings(){ui.runOnIdle{c.sheet="settings"};ui.onNodeWithText("瀏覽",useUnmergedTree=true).performClick()}
    @Test fun switchRevealsConfigurationAndImmediatelyAddsTheAddressButton(){
        ui.onNodeWithTag("home-button").assertDoesNotExist()
        settings()
        ui.onNodeWithTag("home-custom-choice").assertDoesNotExist()
        ui.onNodeWithTag("home-button-switch").performClick()
        ui.onNodeWithTag("home-default-choice").assertIsDisplayed()
        ui.onNodeWithTag("home-custom-choice").assertIsDisplayed()
        f.screenshot("home-settings-default")
        ui.runOnIdle{c.sheet=""}
        val home=ui.onNodeWithTag("home-button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val address=ui.onNodeWithTag("address-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue(home.right<=address.left)
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        settings();ui.onNodeWithTag("home-button-switch").performClick()
        ui.onNodeWithTag("home-custom-choice").assertDoesNotExist()
        ui.runOnIdle{c.sheet=""};ui.onNodeWithTag("home-button").assertDoesNotExist()
    }
    @Test fun invalidUrlDoesNotReplaceSavedHomepageAndSwitchKeepsItsValue(){
        settings();ui.onNodeWithTag("home-button-switch").performClick()
        ui.onNodeWithTag("home-custom-choice").performClick()
        ui.onNodeWithTag("home-url-input").performTextReplacement("example.com/articles")
        ui.onNodeWithTag("save-home-url").performScrollTo().performClick()
        ui.runOnIdle{assertEquals("https://example.com/articles",c.home.destination())}
        ui.onNodeWithTag("home-url-input").performTextReplacement("javascript:alert(1)")
        ui.onNodeWithTag("save-home-url").performScrollTo().performClick()
        ui.onNodeWithTag("home-url-error").assertExists()
        ui.runOnIdle{assertEquals("https://example.com/articles",c.home.destination())}
        ui.onNodeWithTag("home-button-switch").performScrollTo().performClick()
        ui.onNodeWithTag("home-url-input").assertDoesNotExist()
        ui.onNodeWithTag("home-button-switch").performClick()
        ui.onNodeWithTag("home-url-input").assertTextContains("https://example.com/articles")
        f.screenshot("home-settings-custom")
    }
    @Test fun homeNavigatesInPlaceAndBackReturnsToThePage(){
        val page=f.load("https://home-test.invalid/reading","首頁之前的頁面")
        val count=c.tabs.size
        ui.runOnIdle{c.home.updateEnabled(true);c.home.useDefault()}
        ui.onNodeWithTag("home-button").performClick()
        ui.waitUntil(10000){c.active?.url==""&&c.active?.web?.url=="about:blank"}
        ui.onNodeWithTag("browser-home").assertIsDisplayed()
        ui.runOnIdle{assertEquals(page.id,c.activeId);assertEquals(count,c.tabs.size);assertTrue(c.active!!.web.canGoBack())}
        ui.onNodeWithContentDescription("上一頁").performClick()
        ui.waitUntil(10000){c.active?.url=="https://home-test.invalid/reading"}
        ui.runOnIdle{assertEquals(page.id,c.activeId);assertEquals(count,c.tabs.size)}
    }
    @Test fun customHomeDoesNotReplaceNewTabsOrLeakFromPrivateProfile(){
        ui.runOnIdle{c.home.updateEnabled(true);assertTrue(c.home.useCustom("https://practice.chengjing.invalid/"));c.newTab(incognito=false)}
        ui.onNodeWithTag("browser-home").assertIsDisplayed()
        val count=c.tabs.size;val id=c.activeId
        ui.onNodeWithTag("home-button").performClick()
        ui.waitUntil(10000){c.active?.url=="https://practice.chengjing.invalid/"}
        ui.runOnIdle{assertEquals(id,c.activeId);assertEquals(count,c.tabs.size);assertTrue(c.privateSession.supported);c.newTab(incognito=true)}
        val privateId=c.activeId;val history=c.store.history()
        ui.onNodeWithTag("home-button").performClick()
        ui.waitUntil(10000){c.active?.url=="https://practice.chengjing.invalid/"&&c.active?.progress==100}
        ui.runOnIdle{assertEquals(privateId,c.activeId);assertTrue(c.active!!.incognito);assertEquals(history,c.store.history())}
    }
    @Test fun introductionOnlyOnFirstVisitAndSameDayQuoteDoesNotChange(){
        ui.runOnIdle{
            ui.activity.getSharedPreferences("home-v1",Context.MODE_PRIVATE).edit().putBoolean("intro-seen",false).remove("quote-start-day").commit()
            c.newTab(incognito=false)
        }
        ui.onNodeWithTag("home-introduction").assertIsDisplayed();f.screenshot("home-first-visit")
        ui.runOnIdle{c.newTab(incognito=false)}
        ui.onNodeWithTag("home-introduction").assertDoesNotExist()
        ui.onNodeWithTag("daily-home-quote").assertTextEquals("打雷之後，總會下雨")
        ui.runOnIdle{c.newTab(incognito=false)}
        ui.onNodeWithTag("daily-home-quote").assertTextEquals("打雷之後，總會下雨")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("home-introduction").assertDoesNotExist()
        ui.onNodeWithTag("daily-home-quote").assertTextEquals("打雷之後，總會下雨")
        f.screenshot("home-daily-quote")
    }
    @Test fun preferencesSurviveNewInstanceAndExistingUsersSkipIntroduction(){
        val name="home-test-"+UUID.randomUUID()
        val base=ui.activity
        val context=object:ContextWrapper(base){override fun getSharedPreferences(n:String,m:Int)=base.getSharedPreferences(name,m)}
        try{
            val first=HomePreferences(context)
            assertFalse(first.enabled);assertTrue(first.claimIntroduction());assertFalse(first.claimIntroduction())
            first.updateEnabled(true);assertTrue(first.useCustom("techtarian.com"))
            val day=LocalDate.of(2026,9,18);assertEquals(day,first.quoteStart(day))
            val restored=HomePreferences(context)
            assertTrue(restored.enabled);assertEquals("https://techtarian.com/",restored.destination())
            assertFalse(restored.claimIntroduction());assertEquals(day,restored.quoteStart(day.plusDays(2)))
            restored.updateEnabled(false);assertNull(restored.destination());assertEquals("https://techtarian.com/",restored.url)
            base.deleteSharedPreferences(name)
            assertFalse(HomePreferences(context,existingUser=true).claimIntroduction())
        }finally{base.deleteSharedPreferences(name)}
    }
}

/** Each method runs after a real am force-stop in an independent instrumentation process. */
class HomeColdRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val prefs get()=ui.activity.getSharedPreferences("home-cold-test",Context.MODE_PRIVATE)
    @Before fun onlyQa(){assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun seedPreferences(){
        ui.runOnIdle{
            c.home.updateEnabled(true);assertTrue(c.home.useCustom("https://example.com/home?x=1#top"))
            c.home.claimIntroduction()
            val start=c.home.quoteStart(LocalDate.now())
            prefs.edit().putInt("pid",Process.myPid()).putLong("start",start.toEpochDay()).commit()
        }
    }
    @Test fun restorePreferences(){
        assertNotEquals(prefs.getInt("pid",0),Process.myPid())
        ui.runOnIdle{
            assertTrue(c.home.enabled);assertTrue(c.home.custom)
            assertEquals("https://example.com/home?x=1#top",c.home.destination())
            assertFalse(c.home.claimIntroduction())
            assertEquals(prefs.getLong("start",0),c.home.quoteStart(LocalDate.now()).toEpochDay())
            c.home.updateEnabled(false);c.home.useDefault()
        }
        prefs.edit().clear().commit()
    }
}
