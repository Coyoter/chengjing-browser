package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AiSettingsRegressionTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val controller get()=ui.activity.controller

    @Before fun isolate(){
        assumeTrue("Only run against the isolated QA application",ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{
            ui.activity.bookmarkSync.disconnect()
            controller.store.aiProvider="gemma"
            controller.store.saveKey("")
            controller.sheet="settings"
        }
        ui.onNodeWithText("AI").performClick()
    }
    @Test fun selectingOpenRouterImmediatelyShowsKeyFieldWithoutLeavingTab(){
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists().performScrollTo().assertIsDisplayed()
        ui.runOnIdle{assertEquals("openrouter",controller.store.aiProvider)}
    }
    @Test fun selectingGemmaImmediatelyHidesKeyAndRoundTripRestoresIt(){
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists()
        ui.onNodeWithText("Gemma 4 · 手機本機").performScrollTo().performClick()
        ui.onNodeWithTag("openrouter-api-key").assertDoesNotExist()
        ui.onNodeWithTag("download-gemma").assertExists()
        ui.onNodeWithText("OpenRouter · 雲端模型").performScrollTo().performClick()
        ui.onNodeWithTag("openrouter-api-key").assertExists()
    }
    @Test fun providerChoicePersistsAndLaunchLeavesBrowserUsable(){
        ui.onNodeWithText("OpenRouter · 雲端模型").performClick()
        ui.runOnIdle{assertEquals("openrouter",BrowserStore(ui.activity).aiProvider)}
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("browser-controls").assertExists()
        ui.runOnIdle{assertEquals("openrouter",controller.store.aiProvider)}
    }
    @Test fun launchSurfaceDrawsLightAndDarkBackgrounds(){
        ui.runOnUiThread{
            for(dark in listOf(false,true)){
                val view=BrowserLaunchSurface(ui.activity,dark)
                val bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888)
                view.layout(0,0,bitmap.width,bitmap.height)
                view.draw(Canvas(bitmap))
                assertEquals(if(dark)Color.rgb(15,21,19)else Color.rgb(241,238,231),bitmap.getPixel(0,0))
                assertEquals("正在開啟澄境瀏覽器",view.contentDescription)
                bitmap.recycle()
            }
        }
    }
}
