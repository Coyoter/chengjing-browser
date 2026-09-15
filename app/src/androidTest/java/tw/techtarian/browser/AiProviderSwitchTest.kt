package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AiProviderSwitchTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val controller get()=ui.activity.controller
    private var originalProvider:String?=null
    @Before fun prepare(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{
            originalProvider=controller.store.aiProvider
            controller.store.aiProvider="gemma"
            controller.sheet="settings"
        }
        ui.onNodeWithText("AI").performClick()
    }
    @After fun restore(){
        originalProvider?.let{provider->ui.runOnIdle{controller.store.aiProvider=provider;controller.sheet=""}}
    }
    @Test fun providerSwitchImmediatelyShowsAndHidesKeyInput(){
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        // Assert the field exists before scrolling or switching any category.
        ui.onNodeWithTag("openrouter-api-key").assertExists()
        ui.onNodeWithTag("openrouter-api-key").performScrollTo().assertIsDisplayed()
        ui.runOnIdle{
            assertEquals("settings",controller.sheet)
            assertEquals("openrouter",controller.store.aiProvider)
        }
        ui.onNodeWithText("Gemma 4 · 手機本機").performScrollTo().performClick()
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
        ui.onNodeWithText("OpenRouter · 雲端模型").performScrollTo().performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists()
    }
    @Test fun switchingDoesNotSaveOrClearKeysAndProviderPersists(){
        var hadKey=false
        ui.runOnIdle{hadKey=controller.store.hasKey()}
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.onNodeWithTag("openrouter-api-key").performScrollTo().performTextInput("not-a-real-key")
        InstrumentationRegistry.getInstrumentation().runOnMainSync{
            assertEquals(hadKey,controller.store.hasKey())
            val reopened=BrowserStore(ui.activity)
            assertEquals("openrouter",reopened.aiProvider)
        }
    }
}
