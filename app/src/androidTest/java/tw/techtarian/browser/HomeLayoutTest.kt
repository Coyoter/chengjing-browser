package tw.techtarian.browser

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeLayoutTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    @Before fun onlyQa(){assumeTrue(ui.activity.packageName.endsWith(".qa"));ui.waitForIdle()}
    private fun screenshot(name:String){
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).bufferedReader().use{it.readText()}
        ui.waitForIdle();shell("mkdir -p /data/local/tmp/chengjing-ui");shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
    @Test fun allProvidedQuotesFitWithoutClippingAtNarrowWidthAndLargeFonts(){
        val quotes=c.homeQuotes
        assertEquals(89,quotes.size)
        assertEquals("打雷之後，總會下雨",quotes.first());assertEquals("成為你自己",quotes.last())
        assertEquals("How are you？I'm fine, thank you.",quotes[86])
        var phrase by mutableStateOf(quotes.first())
        var scale by mutableFloatStateOf(1f)
        ui.runOnUiThread{ui.activity.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
                MaterialTheme{Surface{Column(Modifier.width(264.dp).fillMaxHeight().verticalScroll(rememberScrollState())){DailyQuoteText(phrase)}}}
            }
        }}
        for(size in listOf(1f,2f)){
            ui.runOnIdle{scale=size}
            for(quote in quotes){
                ui.runOnIdle{phrase=quote}
                val results=mutableListOf<TextLayoutResult>()
                ui.onNodeWithTag("daily-home-quote").assertTextEquals(quote)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(results)}
                assertEquals(1,results.size)
                assertFalse("Clipped sentence at font scale $size: $quote",results.single().hasVisualOverflow)
                assertTrue(results.single().layoutInput.style.fontSize.value>=22f)
                if(quote==quotes.first()){
                    val layout=results.single();val last=layout.lineCount-1
                    assertTrue("Do not leave a single-character widow",last==0||quote.substring(layout.getLineStart(last),layout.getLineEnd(last)).trim().length>=2)
                }
            }
        }
    }
    @Test fun homeButtonFits320DpAtBothTextScalesWithoutChangingControls(){
        var scale by mutableFloatStateOf(1f)
        ui.runOnUiThread{ui.activity.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
                MaterialTheme{Surface{Column(Modifier.width(320.dp)){
                    BrowserAddressBar(TextFieldValue("https://example.com/a/long/path"),{},false,{},false,true,false,20,
                        onGo={},onSecurity={},onReload={},onTabs={},onNewTab={},showHome=true,onHome={})
                }}}
            }
        }}
        for(value in listOf(1f,2f)){
            ui.runOnIdle{scale=value}
            val bar=ui.onNodeWithTag("browser-topbar").fetchSemanticsNode().boundsInRoot
            val home=ui.onNodeWithTag("home-button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val input=ui.onNodeWithTag("address-input").fetchSemanticsNode().boundsInRoot
            val tabs=ui.onNodeWithTag("tab-switcher").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            ui.onNodeWithTag("new-tab-button").assertIsDisplayed()
            assertTrue(home.left>=bar.left&&tabs.right<=bar.right)
            assertTrue(input.width>0f&&home.right<=input.left&&input.right<tabs.left)
        }
        screenshot("home-address-320dp-large-text")
    }
    @Test fun longDailyHomeRendersInLightAndDarkAndKeepsShortcutsReachable(){
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        val longest=c.homeQuotes.withIndex().maxBy{row->row.value.sumOf{if(it.code>127)2 else 1}}.index
        ui.runOnIdle{
            ui.activity.getSharedPreferences("home-v1",android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("intro-seen",true).putLong("quote-start-day",java.time.LocalDate.now().minusDays(longest.toLong()).toEpochDay()).commit()
            c.store.theme="light";c.home.updateEnabled(true);c.newTab(incognito=false)
        }
        ui.runOnUiThread{ui.activity.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)2f else 1f)){
                Box(Modifier.width(320.dp).fillMaxHeight()){
                    key(dark,large){BrowserApp(c,c.store)}
                }
            }
        }}
        for(value in listOf(false,true)){
            ui.runOnIdle{c.store.theme=if(value)"dark"else"light";dark=value}
            ui.onNodeWithTag("daily-home-quote").assertTextEquals(c.homeQuotes[longest])
            screenshot(if(value)"home-long-dark-320dp"else"home-long-light-320dp")
        }
        ui.runOnIdle{large=true}
        ui.onNodeWithTag("daily-home-quote").assertTextEquals(c.homeQuotes[longest])
        screenshot("home-long-large-text-320dp")
        ui.onNodeWithText("所有收藏").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("書籤資料夾").performScrollTo().assertIsDisplayed()
        ui.runOnIdle{c.home.updateEnabled(false);c.store.theme="system"}
    }
}
