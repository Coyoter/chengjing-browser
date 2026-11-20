package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PullRefreshTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val c get() = ui.activity.controller
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script: String): String {
        val latch = CountDownLatch(1); var result = ""
        main { c.active!!.web.evaluateJavascript(script) { result = it; latch.countDown() } }
        check(latch.await(8, TimeUnit.SECONDS)); return result
    }
    private fun until(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < end) {
            if (runCatching(check).getOrDefault(false)) return
            Thread.sleep(100)
        }
        assertTrue("Condition timed out", check())
    }
    private fun open() {
        main { c.newTab("https://practice.chengjing.invalid/") }
        until { eval("document.readyState") == "\"complete\"" && eval("location.hostname") == "\"practice.chengjing.invalid\"" }
        ui.waitForIdle()
    }
    private fun pull() {
        ui.onNodeWithTag("web-content").performTouchInput {
            swipeDown(startY = height * .2f, endY = height * .8f, durationMillis = 650)
        }
    }

    @Before fun isolated() {
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
    }

    @Test fun toolbarReloadButtonIsRightOfForwardAndPullDownNeverReloads() {
        open()
        val backBounds = ui.onNodeWithContentDescription("上一頁").fetchSemanticsNode().boundsInRoot
        val forwardBounds = ui.onNodeWithContentDescription("下一頁").fetchSemanticsNode().boundsInRoot
        val reloadBounds = ui.onNodeWithTag("controls-reload").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val eyeBounds = ui.onNode(hasText("天眼") and hasAnyAncestor(hasTestTag("browser-controls"))).fetchSemanticsNode().boundsInRoot

        assertTrue("Forward must be right of Back", forwardBounds.left >= backBounds.right - 2f)
        assertTrue("Reload button must sit immediately to the right of Back/Forward", reloadBounds.left >= forwardBounds.right - 2f)
        assertTrue("Reload button must sit to the left of Sky Eye", reloadBounds.right <= eyeBounds.left + 2f)

        for (bottom in listOf(false, true)) {
            main { c.sheet = "settings" }
            ui.onNodeWithText(if (bottom) "網址列在下方" else "網址列在上方").performScrollTo().performClick()
            main { c.sheet = "" }; ui.waitForIdle()
            eval("scrollTo(0,0)"); until { eval("scrollY") == "0" }
            val before = eval("performance.timeOrigin")

            // Pulling down from the very top must never trigger a reload anymore.
            pull()
            Thread.sleep(500)
            assertEquals("Pulling down must not reload the page", before, eval("performance.timeOrigin"))

            // Clicking the dedicated Reload button right of Back/Forward reloads the page cleanly.
            ui.onNodeWithTag("controls-reload").performClick()
            until { eval("performance.timeOrigin") != before && eval("document.readyState") == "\"complete\"" }
        }
    }
}
