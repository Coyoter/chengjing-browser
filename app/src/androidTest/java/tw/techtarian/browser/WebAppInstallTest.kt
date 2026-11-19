package tw.techtarian.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WebAppInstallTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val c get() = ui.activity.controller
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        assumeTrue("Never run against production", ui.activity.packageName.endsWith(".qa"))
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/manifest.json" -> MockResponse()
                    .setHeader("Content-Type", "application/manifest+json")
                    .setBody("""{"short_name":"澄境測試應用","name":"澄境測試網頁應用程式","theme_color":"#147A64","display":"standalone"}""")
                "/app" -> MockResponse()
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(
                        """<!doctype html><html><head><title>App Page</title>
                        <link rel="manifest" href="/manifest.json">
                        <meta name="theme-color" content="#147A64">
                        </head><body><h1 id="heading">Hello WebApp</h1></body></html>"""
                    )
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        ui.runOnIdle {
            c.webApps.list().forEach { c.webApps.remove(it.id) }
            c.stopEye()
            c.sheet = ""
        }
    }

    @After fun tearDown() {
        ui.runOnIdle { c.webApps.list().forEach { c.webApps.remove(it.id) } }
        if (::server.isInitialized) server.shutdown()
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(300)
        val dir = File(ui.activity.getExternalFilesDir(null), "webapp-ui").apply { mkdirs() }
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun installManageEditLaunchAndRemoveWebAppFlow() {
        val url = server.url("/app").toString()
        ui.runOnIdle { c.newTab(url, incognito = false) }
        ui.waitUntil(10_000) { c.active?.url == url && c.active?.progress == 100 }

        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:安裝為網頁應用程式").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("install-webapp-dialog").assertIsDisplayed()

        ui.waitUntil(5_000) {
            runCatching {
                ui.onNodeWithTag("webapp-name-input").assertTextContains("澄境測試應用")
                true
            }.getOrDefault(false)
        }
        capture("01-install-dialog-light")

        ui.onNodeWithTag("webapp-mode-minimal_ui").performClick()
        ui.onNodeWithTag("confirm-install-webapp").performClick()
        ui.waitUntil(5_000) { c.webApps.list().size == 1 }

        // If Android's system launcher pin dialog opened on top, capture it and return to the app.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Thread.sleep(700)
        if (device.currentPackageName != ui.activity.packageName) {
            capture("01b-system-pin-dialog")
            device.pressBack()
            val deadline = System.currentTimeMillis() + 5_000
            while (device.currentPackageName != ui.activity.packageName && System.currentTimeMillis() < deadline) {
                Thread.sleep(150)
            }
        }
        ui.waitForIdle()

        val installed = c.webApps.list().single()
        assertEquals("澄境測試應用", installed.name)
        assertEquals(url, installed.url)
        assertEquals(WebAppDisplayMode.MINIMAL_UI, installed.mode)

        // Open the Web Apps manager from the menu and verify the card appears.
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithTag("menu-row:網頁應用程式").performScrollTo().performClick()
        ui.onNodeWithTag("webapp-card:${installed.id}").assertIsDisplayed()
        capture("02-webapp-manager-light")

        // Launch the standalone WebAppActivity directly via its shortcut intent and verify it renders.
        val launchIntent = WebAppLauncher.launchIntent(ui.activity, installed)
        ActivityScenario.launch<WebAppActivity>(launchIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
            Thread.sleep(900)
            capture("03-webapp-window-minimal")
        }

        // Remove the installed web app from the store and verify cleanup.
        ui.runOnIdle {
            assertNotNull(c.webApps.remove(installed.id))
            assertTrue(c.webApps.list().isEmpty())
        }
    }

    @Test fun unregisteredWebAppIntentFinishesSafely() {
        val bogus = Intent(WebAppLauncher.ACTION_OPEN_WEB_APP, Uri.parse("chengjing-webapp://app/wa_deadbeef12345678"))
            .setClass(InstrumentationRegistry.getInstrumentation().targetContext, WebAppActivity::class.java)
            .putExtra(WebAppLauncher.EXTRA_WEB_APP_ID, "wa_deadbeef12345678")
        ActivityScenario.launch<WebAppActivity>(bogus).use { scenario ->
            val end = System.currentTimeMillis() + 5000
            while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && System.currentTimeMillis() < end) {
                Thread.sleep(100)
            }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
