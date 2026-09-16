package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AiProviderSettingsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private var previousProvider:String?=null

    @Before fun openAiSettings(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.waitForIdle()
        ui.runOnIdle{
            val c=ui.activity.controller
            previousProvider=c.store.aiProvider
            c.store.aiProvider="gemma"
            c.sheet="settings"
        }
        ui.onNodeWithText("AI").performClick()
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
    }
    @After fun restoreSelection(){
        previousProvider?.let{provider->ui.runOnIdle{
            ui.activity.controller.store.aiProvider=provider
            ui.activity.controller.sheet=""
        }}
    }
    @Test fun switchingProviderImmediatelyAddsAndRemovesTheKeyField(){
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        // No tab change, activity restart, revision mutation, or manual recomposition.
        ui.onNodeWithTag("openrouter-api-key").assertExists().performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Gemma 4 · 手機本機").performScrollTo().performClick()
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists().performScrollTo().assertIsDisplayed()
        ui.runOnIdle{assertEquals("openrouter",ui.activity.controller.store.aiProvider)}
    }
    @Test fun providerSelectionIsPersistedWithoutSubmittingAnyApiKey(){
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists()
        ui.runOnIdle{assertEquals("openrouter",BrowserStore(ui.activity).aiProvider)}
    }
}
