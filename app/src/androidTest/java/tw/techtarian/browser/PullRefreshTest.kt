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

    @Test fun skyEyeRemainsCenteredWithoutExtraToolbarReloadAndPullDownNeverReloads() {
        open()
        val controlsBounds = ui.onNodeWithTag("browser-controls").fetchSemanticsNode().boundsInRoot
        val backBounds = ui.onNodeWithContentDescription("上一頁").fetchSemanticsNode().boundsInRoot
        val forwardBounds = ui.onNodeWithContentDescription("下一頁").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("controls-reload").assertDoesNotExist()
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        val eyeBounds = ui.onNode(hasText("天眼") and hasAnyAncestor(hasTestTag("browser-controls"))).fetchSemanticsNode().boundsInRoot

        assertEquals("Sky Eye must be horizontally centered", controlsBounds.center.x, eyeBounds.center.x, 1.5f)
        assertEquals("Sky Eye must be vertically centered", controlsBounds.center.y, eyeBounds.center.y, 1.5f)
        assertTrue("Forward must be right of Back", forwardBounds.left >= backBounds.right - 2f)
        assertTrue("Forward must be left of Sky Eye", forwardBounds.right <= eyeBounds.left + 2f)

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

            // Clicking the existing Address Bar Reload button reloads the page cleanly.
            ui.onNodeWithContentDescription("重新整理").performClick()
            until { eval("performance.timeOrigin") != before && eval("document.readyState") == "\"complete\"" }
        }
    }
}
