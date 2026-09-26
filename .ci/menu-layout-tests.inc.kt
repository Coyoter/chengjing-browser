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
        assertTrue(layouts.isNotEmpty());assertTrue(layouts.all{!it.hasVisualOverflow})
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
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onNodeWithTag("close-all-tabs").assertDoesNotExist()
        }
    }
