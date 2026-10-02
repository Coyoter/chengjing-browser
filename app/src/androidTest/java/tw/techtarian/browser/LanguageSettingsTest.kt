package tw.techtarian.browser

import android.content.Context
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LanguageSettingsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    @Before fun setup(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{c.sheet="settings";c.store.theme="light";ui.activity.applyLanguage("zh-TW")}
        ui.onNodeWithTag("language-picker").assertIsDisplayed()
    }
    @After fun restore(){ui.runOnIdle{ui.activity.applyLanguage("zh-TW");c.sheet="";ui.activity.applyAppearance("system")}}
    private fun select(tag:String){
        ui.onNodeWithTag("language-picker").performScrollTo().performClick()
        ui.onNodeWithTag("language:$tag").performScrollTo().performClick()
        ui.waitForIdle()
        ui.runOnIdle{assertEquals(tag,AppLanguages.choice)}
    }
    @Test fun allSeventeenLanguagesCanBeSelectedWithoutRecreatingTabs(){
        val activity=ui.activity;val tabs=c.tabs.map{it.id};val web=c.active!!.web
        for(language in AppLanguagePolicy.languages){
            select(language.tag)
            ui.onNodeWithTag("language-picker").assertTextEquals(language.autonym)
            ui.runOnIdle{
                assertSame(activity,ui.activity);assertSame(web,c.active!!.web);assertEquals(tabs,c.tabs.map{it.id})
                assertEquals(language.tag,AppLanguages.currentTag)
                assertEquals(if(AppLanguagePolicy.isRtl(language.tag))View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,activity.resources.configuration.layoutDirection)
                assertEquals(AppLanguagePolicy.aiLanguage(language.tag),AppLanguagePolicy.aiLanguage(AppLanguages.currentTag))
            }
            screenshot("language-${language.tag}")
        }
    }
    @Test fun selectionPersistsAcrossActivityRecreationAndSystemChoiceUsesPhoneLanguage(){
        select("ja");ui.activityRule.scenario.recreate()
        ui.runOnIdle{assertEquals("ja",AppLanguages.choice);assertEquals("ja",AppLanguages.currentTag);c.sheet="settings"}
        ui.onNodeWithTag("language-picker").assertTextEquals("日本語")
        select("system")
        ui.runOnIdle{assertEquals(AppLanguages.systemTag(ui.activity),AppLanguages.currentTag)}
        ui.activityRule.scenario.recreate()
        ui.runOnIdle{assertEquals("system",AppLanguages.choice);assertEquals(AppLanguages.systemTag(ui.activity),AppLanguages.currentTag)}
    }
    @Test fun darkArabicDropdownUsesTheAppPaletteAndAllLanguageAutonymsStayReachable(){
        select("fr")
        ui.onNodeWithTag("language-picker").performClick()
        screenshot("french-light-language-menu")
        ui.onNodeWithTag("language:ar").performScrollTo().performClick()
        ui.runOnIdle{ui.activity.applyAppearance("dark")}
        ui.onNodeWithTag("language-picker").performClick()
        ui.onNodeWithTag("language:ur").performScrollTo().assertIsDisplayed()
        screenshot("arabic-dark-language-menu")
        ui.onNodeWithTag("language:ur").performClick()
        ui.onNodeWithTag("language-picker").assertTextEquals("اردو")
        ui.runOnIdle{assertEquals(View.LAYOUT_DIRECTION_RTL,ui.activity.resources.configuration.layoutDirection)}
    }
    @Test fun asynchronousModelAndSyncStatusFollowTheChosenLanguage(){
        val before=ui.runOnIdle{c.gemma.status to ui.activity.bookmarkSync.status}
        select("en")
        ui.runOnIdle{assertNotEquals(before.first,c.gemma.status);assertNotEquals(before.second,ui.activity.bookmarkSync.status)}
        select("zh-TW")
        ui.runOnIdle{assertEquals(before.first,c.gemma.status);assertEquals(before.second,ui.activity.bookmarkSync.status)}
    }
    private fun screenshot(name:String){
        ui.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use{it.readBytes()}
        shell("mkdir -p /data/local/tmp/chengjing-languages")
        shell("screencap -p /data/local/tmp/chengjing-languages/$name.png")
    }
}

/** Run separately with testLocale=system on a clean QA install to verify real startup detection. */
class FirstLanguageLaunchTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun firstLaunchUsesPhoneLanguageWithEnglishFallback(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{assertEquals(AppLanguages.systemTag(ui.activity),AppLanguages.currentTag);assertEquals("system",AppLanguages.choice)}
        ui.onNodeWithContentDescription(bt(R.string.msg_f7e587d4b7a5)).assertExists()
    }
}
