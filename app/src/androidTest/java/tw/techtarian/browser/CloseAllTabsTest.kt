package tw.techtarian.browser

import android.content.Context
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CloseAllTabsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    @Before fun isolate(){
        assumeTrue("Only the disposable QA application",ui.activity.packageName.endsWith(".qa"))
        f.reset();ui.waitUntil(15000){!c.privateSession.clearing}
    }
    @After fun cleanup(){ui.runOnIdle{c.closePrivateTabs();c.sheet="";c.updatePrivacyWindow()}}

    @Test fun cancellingLeavesAllTabsAndPreviewsIntact(){
        val first=f.load("https://close-all.invalid/same","閱讀甲")
        val second=f.load("https://close-all.invalid/same","閱讀乙")
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        val ids=c.tabs.map{it.id}
        val keys=listOf(first.previewKey!!,second.previewKey!!)
        val hashes=keys.associateWith{f.hash(it)}
        ui.onNodeWithTag("close-all-tabs").assertIsDisplayed().performClick()
        ui.onNodeWithText("關閉所有一般分頁？").assertIsDisplayed()
        f.screenshot("close-all-confirm-light")
        ui.onNodeWithTag("cancel-close-all-tabs").performClick()
        assertEquals(ids,c.tabs.map{it.id})
        keys.forEach{assertEquals(hashes[it],f.hash(it))}
    }
    @Test fun normalGroupCloseLeavesPrivateTabsAndSavedCollectionsUntouched(){
        f.load("https://close-all.invalid/a","一般甲")
        f.load("https://close-all.invalid/b","一般乙")
        ui.runOnIdle{
            c.checkpointTabs();assertTrue(c.privateSession.supported)
            c.store.bookmark("https://close-all.invalid/bookmark","保留的書籤")
            c.favorites.save("https://close-all.invalid/favorite","保留的收藏",300.0,.3)
        }
        val keys=c.tabs.mapNotNull{it.previewKey}
        val normalIds=c.tabs.filterNot{it.incognito}.map{it.id}
        val privateTab=f.load("https://close-all.invalid/private","無痕保留",true)
        val history=c.store.history();val favorites=c.favorites.all();val bookmarks=c.store.bookmarks()
        ui.runOnIdle{
            assertEquals(normalIds.size,c.closeAllTabs(false))
            assertEquals(listOf(privateTab.id),c.tabs.map{it.id})
            assertEquals(privateTab.id,c.activeId)
            assertFalse(c.privateSession.clearing)
            assertTrue(c.store.tabs().isEmpty())
            assertEquals(0,c.closeAllTabs(false))
        }
        assertTrue(c.previews.flush());keys.forEach{assertFalse(f.file(it).exists())}
        assertEquals(history,c.store.history());assertEquals(favorites,c.favorites.all());assertEquals(bookmarks,c.store.bookmarks())
    }
    @Test fun closingPrivateGroupPreservesRegularThumbnailsAndManifest(){
        val normal=f.load("https://close-all.invalid/normal","保留一般頁面")
        ui.runOnIdle{c.checkpointTabs();assertTrue(c.privateSession.supported)}
        val key=normal.previewKey!!;val hash=f.hash(key)
        val records=c.store.savedTabRecords()
        f.load("https://close-all.invalid/private-a","無痕甲",true)
        f.load("https://close-all.invalid/private-b","無痕乙",true)
        ui.runOnIdle{assertEquals(2,c.closeAllTabs(true));assertTrue(c.tabs.none{it.incognito})}
        ui.waitUntil(15000){!c.privateSession.clearing}
        assertEquals(records,c.store.savedTabRecords());assertEquals(hash,f.hash(key))
        assertTrue(c.tabs.any{it.id==normal.id})
    }
    @Test fun closeWholeSessionShowsOnlyAFreshHomeAndDoesNotRecloseIt(){
        val a=f.load("https://close-all.invalid/a","全部關閉甲")
        val b=f.load("https://close-all.invalid/b","全部關閉乙")
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        val oldIds=c.tabs.map{it.id}.toSet()
        ui.onNodeWithTag("close-all-tabs").performClick()
        ui.onNodeWithTag("confirm-close-all-tabs").performClick()
        ui.onNodeWithTag("browser-controls").assertIsDisplayed()
        ui.runOnIdle{
            assertEquals("",c.sheet);assertEquals(1,c.tabs.size)
            assertTrue(c.active!!.url.isEmpty());assertFalse(c.active!!.id in oldIds)
            assertFalse(c.active!!.incognito);assertEquals(listOf(""),c.store.tabs())
        }
        assertTrue(c.previews.flush())
        assertFalse(f.file(a.previewKey!!).exists());assertFalse(f.file(b.previewKey!!).exists())
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        ui.runOnIdle{assertEquals(listOf(""),c.tabs.map{it.url});assertEquals(listOf(""),c.store.tabs())}
    }
    @Test fun emptyGroupHasNoDestructiveAction(){
        ui.runOnIdle{c.openTabOverview()}
        ui.onNodeWithTag("tab-group-true").performClick()
        ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        ui.runOnIdle{assertEquals(0,c.closeAllTabs(true))}
        ui.onNodeWithTag("tab-group-false").performClick()
        ui.onNodeWithTag("close-all-tabs").assertIsDisplayed()
        f.screenshot("close-all-normal-list")
    }
    @Test fun closingInDarkModeDoesNotAlterTheFirstToolbar(){
        ui.runOnIdle{c.store.theme="dark"}
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        ui.runOnIdle{c.openTabOverview()}
        ui.onNodeWithTag("close-all-tabs").performClick()
        f.screenshot("close-all-confirm-dark")
        ui.onNodeWithTag("cancel-close-all-tabs").performClick()
        ui.runOnIdle{c.sheet=""}
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.runOnIdle{c.store.theme="system"}
    }
}

/** Invoke each method in a separate process; never operate on the production package. */
class CloseAllColdRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    private val prefs get()=ui.activity.getSharedPreferences("close-all-cold-test",Context.MODE_PRIVATE)
    @Before fun onlyQa(){assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun closeAndCheckpoint(){
        f.reset()
        f.load("https://close-all.invalid/restart-a","重啟不還原甲")
        f.load("https://close-all.invalid/restart-b","重啟不還原乙")
        ui.runOnIdle{c.checkpointTabs()}
        val keys=c.tabs.mapNotNull{it.previewKey}
        ui.runOnIdle{assertEquals(c.tabs.size,c.closeAllTabs(false));c.checkpointTabs()}
        assertTrue(c.previews.flush());keys.forEach{assertFalse(f.file(it).exists())}
        assertTrue(prefs.edit().putString("keys",JSONArray(keys).toString()).putInt("pid",Process.myPid()).commit())
    }
    @Test fun allClosedPagesRemainClosedAfterProcessRestart(){
        assertNotEquals(prefs.getInt("pid",0),Process.myPid())
        val keys=JSONArray(prefs.getString("keys",null))
        ui.runOnIdle{
            assertEquals(listOf(""),c.tabs.map{it.url})
            assertEquals(listOf(""),c.store.tabs())
            for(i in 0 until keys.length()){
                val key=keys.getString(i)
                assertTrue(c.tabs.none{it.previewKey==key});assertFalse(f.file(key).exists())
            }
            c.openTabOverview()
        }
        f.screenshot("close-all-after-process-restart")
        prefs.edit().clear().commit()
    }
}
