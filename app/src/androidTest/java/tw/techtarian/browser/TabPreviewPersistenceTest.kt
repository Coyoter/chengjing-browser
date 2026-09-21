package tw.techtarian.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class PreviewFixture(private val ui:androidx.compose.ui.test.junit4.AndroidComposeTestRule<androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity>,MainActivity>) {
    val c get()=ui.activity.controller
    fun reset(){
        ui.runOnIdle{
            c.closePrivateTabs();c.sheet="";c.stopEye()
            c.tabs.toList().forEach{c.closeTab(it.id)}
            ui.activity.bookmarkSync.disconnect()
        }
    }
    fun load(url:String,title:String,incognito:Boolean=false):BrowserTab {
        val tab=AtomicReference<BrowserTab>()
        ui.runOnIdle{tab.set(c.newTab(incognito=incognito)!!)}
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>$title</title></head><body style='margin:0;background:#f1eee7;color:#20352c;font:20px sans-serif'><header style='background:#147a64;color:white;padding:24px'>$title</header><main style='padding:24px'><h1>分頁還在，快照就該還在。</h1><p>這是本機合成測試頁，不需要連線。</p></main></body></html>"
        ui.runOnUiThread{tab.get().web.loadDataWithBaseURL(url,html,"text/html","UTF-8",null)}
        // Preview readiness can precede full loading; ordinary fixtures need both.
        ui.waitUntil(15000){tab.get().title==title&&tab.get().pendingUrl.isEmpty()&&(incognito||tab.get().previewReady)}
        ui.waitForIdle()
        return tab.get()
    }
    fun file(key:String)=File(c.previews.directory,"$key.png")
    fun hash(key:String)=MessageDigest.getInstance("SHA-256").digest(file(key).readBytes()).joinToString(""){"%02x".format(it)}
    fun saved(keys:Set<String>):Boolean=c.tabs.filter{it.previewKey in keys}.let{it.size==keys.size&&it.all{tab->tab.preview!=null}}
    fun screenshot(name:String){
        ui.waitForIdle()
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
        shell("mkdir -p /data/local/tmp/chengjing-ui")
        shell("screencap -p /data/local/tmp/chengjing-ui/$name.png")
    }
}

class TabPreviewPersistenceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val f get()=PreviewFixture(ui)
    private val c get()=ui.activity.controller
    @Before fun isolate(){assumeTrue(ui.activity.packageName.endsWith(".qa"));f.reset()}
    @Test fun recreationRestoresEveryThumbnailWithoutVisitingBackgroundTabs(){
        val a=f.load("https://preview-restart.invalid/one","第一份閱讀")
        val b=f.load("https://preview-restart.invalid/two","第二份閱讀")
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        val keys=setOf(a.previewKey!!,b.previewKey!!)
        assertTrue(keys.all{f.file(it).isFile})
        ui.activityRule.scenario.recreate()
        ui.waitUntil(15000){f.saved(keys)}
        ui.runOnIdle{c.openTabOverview()}
        assertEquals(keys,c.tabs.mapNotNull{it.previewKey}.filter{it in keys}.toSet())
        f.screenshot("previews-after-recreation")
    }
    @Test fun closeTabDeletesOnlyItsSnapshotAndDoesNotReuseItsIdentity(){
        val a=f.load("https://preview-restart.invalid/same","同網址第一份")
        val b=f.load("https://preview-restart.invalid/same","同網址第二份")
        ui.runOnIdle{c.checkpointTabs()}
        val keyA=a.previewKey!!;val keyB=b.previewKey!!
        ui.runOnIdle{c.closeTab(a.id)};assertTrue(c.previews.flush())
        assertFalse(f.file(keyA).exists());assertTrue(f.file(keyB).exists())
        ui.activityRule.scenario.recreate();ui.waitUntil(15000){f.saved(setOf(keyB))}
        ui.runOnIdle{
            assertTrue(c.tabs.none{it.previewKey==keyA})
            val new=c.newTab(incognito=false)!!;assertNotEquals(keyA,new.previewKey)
        }
    }
    @Test fun reloadDoesNotEraseLastSuccessfulPreview(){
        val tab=f.load("https://preview-restart.invalid/reload","最後成功的畫面")
        ui.runOnIdle{c.checkpointTabs()}
        val key=tab.previewKey!!;val before=f.hash(key)
        ui.runOnIdle{
            tab.web.webViewClient.onPageStarted(tab.web,tab.url,null)
            c.openTabOverview();c.checkpointTabs()
            assertNotNull(tab.preview)
        }
        assertEquals(before,f.hash(key))
    }
    @Test fun privatePagesHaveNoDiskIdentityOrSnapshot(){
        f.load("https://preview-restart.invalid/normal","一般畫面")
        ui.runOnIdle{c.checkpointTabs();assertTrue(c.privateSession.supported)}
        val normalKeys=c.store.savedTabRecords().map{it.key}.toSet()
        val private=f.load("https://preview-restart.invalid/private","私密測試",true)
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs();assertNull(private.previewKey);assertNull(private.preview)}
        assertEquals(normalKeys,c.store.savedTabRecords().map{it.key}.toSet())
        assertFalse(c.store.tabs().any{it.contains("/private")})
        ui.runOnIdle{c.closePrivateTabs()}
    }
}

/** Run methods in separate instrumentation invocations, with a real am force-stop between them. */
class TabPreviewColdRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val f get()=PreviewFixture(ui)
    private val c get()=ui.activity.controller
    private val prefs get()=ui.activity.getSharedPreferences("preview-cold-test",Context.MODE_PRIVATE)
    @Before fun onlyQa(){assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @Test fun seed(){
        f.reset()
        val tabs=listOf(f.load("https://preview-restart.invalid/cold-a","重開後保留第一頁"),f.load("https://preview-restart.invalid/cold-b","重開後保留第二頁"))
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs();tabs.forEach{it.previewReady=false}}
        val rows=JSONArray(tabs.map{JSONObject().put("key",it.previewKey).put("hash",f.hash(it.previewKey!!))})
        assertTrue(prefs.edit().putString("expected",rows.toString()).putInt("seedPid",Process.myPid()).commit())
        f.screenshot("previews-before-force-stop")
    }
    @Test fun restoreAndClose(){
        assertNotEquals("A new process is required, not just Activity recreation",prefs.getInt("seedPid",0),Process.myPid())
        val rows=JSONArray(prefs.getString("expected",null));assertEquals(2,rows.length())
        val keys=(0 until rows.length()).map{rows.getJSONObject(it).getString("key")}.toSet()
        ui.waitUntil(15000){f.saved(keys)}
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);assertEquals(row.getString("hash"),f.hash(row.getString("key")))
        }
        ui.runOnIdle{c.openTabOverview()};f.screenshot("previews-after-force-stop")
        val removed=rows.getJSONObject(0).getString("key");val kept=rows.getJSONObject(1).getString("key")
        ui.runOnIdle{c.closeTab(c.tabs.single{it.previewKey==removed}.id);c.checkpointTabs()}
        assertFalse(f.file(removed).exists());assertTrue(f.file(kept).exists())
        assertTrue(prefs.edit().putString("removed",removed).putString("kept",kept).putInt("restorePid",Process.myPid()).commit())
    }
    @Test fun closedTabStaysDeleted(){
        assertNotEquals(prefs.getInt("restorePid",0),Process.myPid())
        val removed=prefs.getString("removed",null)!!;val kept=prefs.getString("kept",null)!!
        ui.waitUntil(15000){f.saved(setOf(kept))}
        ui.runOnIdle{assertTrue(c.tabs.none{it.previewKey==removed});c.openTabOverview()}
        assertFalse(f.file(removed).exists());assertTrue(f.file(kept).exists())
        f.screenshot("previews-only-closed-tab-removed")
        prefs.edit().clear().commit()
    }
}
