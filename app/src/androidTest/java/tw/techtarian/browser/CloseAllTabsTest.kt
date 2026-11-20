package tw.techtarian.browser

import android.content.Context
import android.os.Process
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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

    private fun openOverflow(){
        ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        ui.onNode(hasTestTag("tab-overview-menu") and hasAnyAncestor(hasTestTag("panel-header"))).assertIsDisplayed().performClick()
        ui.onNodeWithTag("close-all-tabs").assertIsDisplayed()
    }
    private fun chooseCloseAll(){openOverflow();ui.onNodeWithTag("close-all-tabs").assertIsEnabled().performClick()}
    private fun dismissOverflow(){
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        ui.waitForIdle()
        ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        ui.onNodeWithTag("tab-grid").assertExists()
    }

    @Test fun cancellingLeavesAllTabsAndPreviewsIntact(){
        val first=f.load("https://close-all.invalid/same","閱讀甲")
        val second=f.load("https://close-all.invalid/same","閱讀乙")
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        val ids=c.tabs.map{it.id}
        val keys=listOf(first.previewKey!!,second.previewKey!!)
        val hashes=keys.associateWith{f.hash(it)}
        chooseCloseAll()
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
        chooseCloseAll()
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
        openOverflow()
        ui.onNodeWithTag("close-all-tabs").assertIsNotEnabled()
        ui.runOnIdle{assertEquals(0,c.closeAllTabs(true))}
        dismissOverflow()
        ui.onNodeWithTag("tab-group-false").performClick()
        ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        ui.onNodeWithTag("new-overview-tab").assertIsDisplayed()
        openOverflow();ui.onNodeWithTag("close-all-tabs").assertIsEnabled();dismissOverflow()
    }
    @Test fun closingInDarkModeDoesNotAlterTheFirstToolbar(){
        ui.runOnIdle{c.store.theme="dark"}
        ui.activityRule.scenario.recreate();ui.waitForIdle()
        f.load("https://close-all.invalid/dark-a","深色閱讀甲")
        f.load("https://close-all.invalid/dark-b","深色閱讀乙")
        ui.runOnIdle{c.openTabOverview()}
        f.screenshot("tabs-list-dark")
        openOverflow();f.screenshot("tabs-overflow-dark")
        ui.onNodeWithTag("close-all-tabs").performClick()
        f.screenshot("close-all-confirm-dark")
        ui.onNodeWithTag("cancel-close-all-tabs").performClick()
        ui.runOnIdle{c.sheet=""}
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("browser-controls"))).assertCountEquals(5)
        ui.onNodeWithTag("share-current-page").assertDoesNotExist()
        ui.runOnIdle{c.store.theme="system"}
    }
    @Test fun onlyAddTabAppearsInFooterAndMenuIsAnchoredAtTopRight(){
        f.load("https://close-all.invalid/layout-a","閱讀，留給自己")
        f.load("https://close-all.invalid/layout-b","讓每個想法有位置")
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("tab-overview-footer"))).assertCountEquals(1)
        val footer=ui.onNodeWithTag("tab-overview-footer").fetchSemanticsNode().boundsInRoot
        val add=ui.onNodeWithTag("new-overview-tab").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val header=ui.onNodeWithTag("panel-header").fetchSemanticsNode().boundsInRoot
        val more=ui.onNodeWithTag("tab-overview-menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Add button must use the footer's available width",add.width>footer.width*.85f)
        assertTrue("More icon belongs at the top right",more.center.x>header.center.x)
        assertTrue("Add button remains at the bottom",add.top>header.bottom)
        f.screenshot("tabs-list-light")
        val ids=c.tabs.map{it.id}
        openOverflow();f.screenshot("tabs-overflow-light")
        dismissOverflow()
        assertEquals(ids,c.tabs.map{it.id})
        ui.runOnIdle{c.sheet="menu"}
        ui.onNodeWithTag("tab-overview-menu").assertDoesNotExist()
        ui.onNodeWithContentDescription("關閉選單").assertIsDisplayed()
    }
    @Test fun footerAddsToSelectedCollectionWithoutDeletingExistingTabs(){
        ui.runOnIdle{c.openTabOverview();assertTrue(c.privateSession.supported)}
        val normalBefore=c.tabs.filterNot{it.incognito}.map{it.id}
        ui.onNodeWithTag("new-overview-tab").performClick()
        ui.runOnIdle{
            assertFalse(c.active!!.incognito)
            assertEquals(normalBefore.size+1,c.tabs.count{!it.incognito})
            assertTrue(c.tabs.map{it.id}.containsAll(normalBefore))
            c.openTabOverview()
        }
        val retainedNormal=c.tabs.filterNot{it.incognito}.map{it.id}
        ui.onNodeWithTag("tab-group-true").performClick()
        ui.onNodeWithTag("new-overview-tab").assertIsDisplayed().performClick()
        ui.runOnIdle{
            assertTrue(c.active!!.incognito)
            assertEquals(retainedNormal,c.tabs.filterNot{it.incognito}.map{it.id})
        }
    }
    @Test fun overflowClosesSelectedNormalGroupWhilePrivatePageIsActive(){
        f.load("https://close-all.invalid/normal-menu","一般頁面")
        ui.runOnIdle{assertTrue(c.privateSession.supported)}
        val privateTab=f.load("https://close-all.invalid/private-menu","無痕保留",true)
        ui.runOnIdle{c.openTabOverview()}
        ui.onNodeWithTag("tab-group-false").performClick()
        chooseCloseAll()
        ui.onNodeWithText("關閉所有一般分頁？").assertIsDisplayed()
        ui.onNodeWithTag("confirm-close-all-tabs").performClick()
        ui.runOnIdle{assertEquals(listOf(privateTab.id),c.tabs.map{it.id});assertTrue(c.store.tabs().isEmpty())}
        ui.onNodeWithTag("tab-group-true").performClick()
        chooseCloseAll()
        ui.onNodeWithText("關閉所有無痕分頁？").assertIsDisplayed()
        ui.onNodeWithTag("cancel-close-all-tabs").performClick()
        ui.runOnIdle{assertEquals(listOf(privateTab.id),c.tabs.map{it.id})}
    }
    /** Check the painted surface and actual Text, not merely a full-width text wrapper. */
    private fun assertCompactCenteredMenu(compact:Boolean) {
        val surface=ui.onNodeWithTag("tab-overview-menu-surface",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action=ui.onNodeWithTag("close-all-tabs").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val label=ui.onNodeWithTag("close-all-tabs-label",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density=ui.density.density
        assertEquals("Horizontal text centering",surface.center.x,label.center.x,1f)
        assertEquals("Vertical text centering",surface.center.y,label.center.y,1f)
        assertEquals("No painted but untappable vertical padding",surface.height,action.height,1f)
        assertTrue("Keep a 48 dp touch target",action.height>=48f*density-1f)
        assertTrue(label.left>=surface.left+15f*density&&label.right<=surface.right-15f*density)
        assertTrue(label.top>=surface.top&&label.bottom<=surface.bottom)
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithTag("close-all-tabs-label",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue("Text must expose its measured layout",layouts.isNotEmpty())
        val details=layouts.joinToString{r->"size=${r.size}, paragraph=${r.multiParagraph.width}x${r.multiParagraph.height}, lines=${r.lineCount}, widthOverflow=${r.didOverflowWidth}, heightOverflow=${r.didOverflowHeight}"}
        println("MENU_GEOMETRY surface=$surface label=$label layouts=$details")
        assertTrue("Label must not be clipped: $details",layouts.all{!it.hasVisualOverflow})
        if(compact){
            assertTrue("Default surface should not keep the old 188 dp minimum",surface.width/density in 143f..160f)
            assertTrue("Single action should not paint an oversized 64 dp panel",surface.height/density in 47f..50f)
        }
    }
    @Test fun compactOverflowCentersTextInLightAndDarkWithoutChangingTheFooter(){
        f.load("https://close-all.invalid/menu-detail-a","閱讀，留給自己")
        f.load("https://close-all.invalid/menu-detail-b","魔鬼藏在細節裡")
        for(theme in listOf("light","dark")){
            ui.runOnIdle{c.sheet="";ui.activity.applyAppearance(theme)}
            ui.waitForIdle()
            ui.runOnIdle{c.openTabOverview()}
            val ids=c.tabs.map{it.id}
            openOverflow();assertCompactCenteredMenu(compact=true)
            f.screenshot("compact-tab-menu-$theme")
            dismissOverflow();assertEquals(ids,c.tabs.map{it.id})
            ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("tab-overview-footer"))).assertCountEquals(1)
            ui.onNodeWithTag("new-overview-tab").assertIsDisplayed()
        }
        ui.runOnIdle{ui.activity.applyAppearance("system")}
    }
    @Test fun compactOverflowGrowsForLargeFontsAndCentersInBothLayoutDirections(){
        val controller=c
        ui.runOnIdle{c.openTabOverview()}
        var fontScale by androidx.compose.runtime.mutableFloatStateOf(1f)
        var direction by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.LayoutDirection.Ltr)
        ui.runOnUiThread{ui.activity.setContent{
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,fontScale),
                androidx.compose.ui.platform.LocalLayoutDirection provides direction
            ){
                androidx.compose.material3.MaterialTheme{
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.width(320.dp).height(240.dp),contentAlignment=androidx.compose.ui.Alignment.TopEnd
                    ){TabOverviewMenu(controller)}
                }
            }
        }}
        for(scale in listOf(1f,2f))for(layout in listOf(androidx.compose.ui.unit.LayoutDirection.Ltr,androidx.compose.ui.unit.LayoutDirection.Rtl)){
            ui.runOnIdle{fontScale=scale;direction=layout}
            ui.onNodeWithTag("tab-overview-menu").performClick()
            assertCompactCenteredMenu(compact=scale==1f)
            val surface=ui.onNodeWithTag("tab-overview-menu-surface",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue("Large-font menu must fit a narrow window",surface.width/ui.density.density<=280f+1f)
            f.screenshot("compact-tab-menu-scale-$scale-$layout")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        }
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
