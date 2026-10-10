package tw.techtarian.browser

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AddressEditingTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val url="https://www.google.com.tw/search?q="+"long-address-".repeat(12)+"end"
    private val address=TextFieldState(url,initialSelection=TextRange.Zero)
    private var editing by mutableStateOf(false)
    private val handle=SemanticsMatcher("Cursor handle") { node ->
        node.config.any { it.key.name=="SelectionHandleInfo" && it.value.toString().contains("handle=Cursor") }
    }

    private fun open(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnUiThread { ui.activity.setContent {
            MaterialTheme { Column(Modifier.fillMaxSize().safeDrawingPadding()) { Column(Modifier.width(320.dp)) {
                BrowserAddressBar(address,editing,{editing=it},false,true,false,1,
                    onGo={},onSecurity={},onReload={},onTabs={},onNewTab={},showHome=true)
            } } }
        } }
        val input=ui.onNodeWithTag("address-input")
        input.performClick()
        ui.runOnIdle { assertEquals(0,address.selection.start);assertEquals(url.length,address.selection.end) }
        // Establish the start viewport only; the assertions below use real finger drags.
        input.performTextInputSelection(TextRange(0))
        input.performTouchInput { advanceEventTime(600);click(Offset(width/2f,height/2f)) }
        ui.waitForIdle()
        assertTrue("Tap must collapse selection: ${address.selection}",address.selection.collapsed)
    }

    /** Inject an actual held finger, allowing layout/auto-scroll between drag events. */
    private fun dragCursor(right:Boolean){
        val input=ui.onNodeWithTag("address-input").fetchSemanticsNode()
        val cursor=ui.onNode(handle,useUnmergedTree=true).fetchSemanticsNode()
        val origin=cursor.layoutInfo.coordinates.positionOnScreen()
        val start=origin+Offset(cursor.size.width/2f,cursor.size.height/2f)
        val field=input.layoutInfo.coordinates.positionOnScreen()
        val target=Offset(if(right)field.x+input.size.width+30f else field.x-30f,start.y)
        dragFinger(start,target){address.selection.start==if(right)url.length else 0}
    }

    private fun dragFinger(start:Offset,target:Offset,holdMillis:Long=0,done:()->Boolean){
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime=SystemClock.uptimeMillis()
        fun event(action:Int,p:Offset){
            val motion=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,p.x,p.y,0)
            motion.source=InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(motion,true)) } finally { motion.recycle() }
        }
        event(MotionEvent.ACTION_DOWN,start)
        try {
            if(holdMillis>0){ui.mainClock.advanceTimeBy(holdMillis);SystemClock.sleep(holdMillis)}
            for(step in 1..15){event(MotionEvent.ACTION_MOVE,start+(target-start)*(step/15f));SystemClock.sleep(16);ui.waitForIdle()}
            for(step in 1..url.length*2){
                event(MotionEvent.ACTION_MOVE,target+Offset(if(step%2==0)1f else 0f,0f))
                // Android input/IME and selection auto-scroll use real VSYNC time,
                // independently of Compose's auto-advancing test clock.
                SystemClock.sleep(16)
                ui.waitForIdle()
                if(done())break
            }
        } finally { event(MotionEvent.ACTION_UP,target) }
        ui.waitForIdle()
    }

    @Test fun cursorDragReachesHiddenEndAndReturnsToBeginning(){
        open()
        val initiallyVisibleCursor=address.selection.start
        assertTrue(initiallyVisibleCursor in 1 until url.length)
        dragCursor(true)
        ui.runOnIdle {
            assertEquals("Dragging past the right edge must reach the hidden URL suffix",url.length,address.selection.start)
            assertTrue("Tap must collapse selection: ${address.selection}",address.selection.collapsed)
            assertEquals(url,address.text.toString())
        }
        dragCursor(false)
        ui.runOnIdle { assertEquals("Dragging past the left edge must return to the URL start",0,address.selection.start);assertEquals(url,address.text.toString()) }
        ui.onNodeWithTag("address-input").performTextInput("prefix-")
        ui.runOnIdle { assertEquals("prefix-$url",address.text.toString()) }
    }

    @Test fun longPressSelectionCanExtendIntoHiddenTextInBottomDarkAddressBar(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        val c=ui.activity.controller
        val originalBottom=c.store.addressAtBottom
        try {
            ui.runOnIdle { c.store.addressAtBottom=true;ui.activity.applyAppearance("dark");c.newTab() }
            ui.activityRule.scenario.recreate()
            val input=ui.onNodeWithTag("address-input")
            input.performClick().performTextReplacement(url)
            input.performTextInputSelection(TextRange(0))
            // Wait for Android's IME resize, which is outside Compose's test clock.
            ui.waitUntil(10000){
                val decor=ui.activity.window.decorView
                val ime=androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom?:0
                val node=input.fetchSemanticsNode()
                ime>0 && node.layoutInfo.coordinates.positionOnScreen().y+node.size.height<=decor.height-ime+1
            }
            ui.waitForIdle()
            val field=input.fetchSemanticsNode()
            val origin=field.layoutInfo.coordinates.positionOnScreen()
            val start=origin+Offset(8f,field.size.height/2f)
            val target=origin+Offset(field.size.width+30f,field.size.height/2f)
            dragFinger(start,target,holdMillis=800){input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].max==url.length}
            screenshot("bottom-dark-selection")
            val selection=input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
            assertEquals("Long-press selection must reach the hidden suffix",url.length,selection.max)
            assertFalse(selection.collapsed)
            input.assertTextEquals(url)
        } finally { ui.runOnIdle { ui.activity.controller.store.addressAtBottom=originalBottom;ui.activity.applyAppearance("system") } }
    }

    @Test fun actualAddressBarUpdatesSuggestionsSubmitsSearchAndReflectsTabChanges(){
        assumeTrue(ui.activity.packageName.endsWith(".qa"))
        val c=ui.activity.controller
        val searchSettings=c.store.searchSettings
        try {
            ui.runOnIdle {
                c.store.clearHistory();c.store.useSearchEngine("bing");c.sheet=""
                c.store.recordSearch("澄境閱讀",SearchEngines.searchUrl("澄境閱讀",c.store.searchSettings))
                c.newTab()
            }
            val input=ui.onNodeWithTag("address-input")
            input.performClick().performTextReplacement("澄境")
            ui.onNodeWithTag("address-suggestions").assertIsDisplayed()
            ui.onNodeWithText("澄境閱讀").assertExists()
            input.performImeAction()
            ui.waitUntil(10000){c.active?.url?.startsWith("https://www.bing.com/search?q=%E6%BE%84%E5%A2%83")==true}
            ui.onNodeWithTag("address-suggestions").assertDoesNotExist()
            input.assertTextEquals("https://www.bing.com/search?q=%E6%BE%84%E5%A2%83")
            assertEquals("Idle address must show the beginning of the URL",TextRange.Zero,input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            ui.runOnIdle { c.newTab() }
            assertEquals("",input.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            ui.onNodeWithText("搜尋或輸入網址").assertIsDisplayed()
            screenshot("new-tab-address")
        } finally { ui.runOnIdle { c.store.useSearchEngine(searchSettings.engineId);c.store.clearHistory() } }
    }

    private fun screenshot(name:String){
        ui.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use{it.readBytes()}
        shell("mkdir -p /data/local/tmp/chengjing-address")
        shell("screencap -p /data/local/tmp/chengjing-address/$name.png")
    }
}
