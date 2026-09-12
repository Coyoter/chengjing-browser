package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class EngineVersionJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    @Test fun showsTheRealEngineAndQueriesTheOfficialStableRelease(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        val installed=ChromiumVersions.installed(ui.activity)
        assertNotNull(ChromiumVersions.compare(installed.version,installed.version))
        // A user-specified UA must not turn the engine-version label into the spoofed version.
        ui.runOnIdle{c.setUserAgent("custom","Mozilla/5.0 Chrome/999.0.0.0");c.sheet="settings"}
        assertEquals(installed.version,ChromiumVersions.installed(ui.activity).version)
        val repository=EngineVersionRepository(ui.activity,installed.platform)
        val official=runBlocking{repository.refresh()}
        assertNotNull(ChromiumVersions.compare(official.version,official.version))
        ui.onNodeWithText("瀏覽",substring=false).performClick()
        ui.onNodeWithTag("engine-version-comparison").performScrollTo()
        ui.onNodeWithTag("engine-current").assertTextEquals(installed.version)
        ui.onNodeWithTag("engine-stable").assertTextEquals(official.version)
        ui.onNodeWithText("重新檢查版本").performScrollTo().performClick()
        ui.waitUntil(20000){(repository.cached()?.checkedAt?:0)>official.checkedAt && ui.onAllNodesWithText("重新檢查版本").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("暫時查不到官方版本",substring=true).assertDoesNotExist()
        ui.onNodeWithText("開啟核心更新頁").assertExists()
        ui.waitForIdle();Thread.sleep(400)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"09-engine-versions.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.9-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        ui.runOnIdle{c.setUserAgent("chrome","");c.sheet=""}
    }
}
