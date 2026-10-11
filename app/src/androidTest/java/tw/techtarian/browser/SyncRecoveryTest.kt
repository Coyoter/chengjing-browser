package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Same Drive protocol over a local fixture. Never authorizes or writes a user's Google account. */
class SyncRecoveryTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val store get()=c.store.bookmarkStore
    private val sync get()=ui.activity.bookmarkSync
    private val server=MockWebServer()
    private val snapshots=ConcurrentHashMap<String,String>()
    private val failures=AtomicInteger()
    private val patches=AtomicInteger()
    private val uploadPaths=java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var failureCode=503
    private var slow=false
    @Before fun setup(){
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{
            sync.destroy();store.connected=false;store.accountId="";store.accountLabel="";store.lastSync=123;store.replace(emptyList())
            c.store.syncSiteSettings=true
        }
        val device=store.deviceId
        snapshots["bookmarks"]=BookmarkFormat.snapshot(emptyList())
        snapshots["favorites"]=FavoriteFormat.snapshot(c.favorites.records())
        snapshots["sites"]=SiteSettingsFormat.write(c.store.siteRecords())
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            // A real read timeout is still forced, without imposing a 200 ms
            // deadline on unrelated successful responses during cold emulator startup.
            if(failures.getAndUpdate{maxOf(0,it-1)}>0)return if(slow)MockResponse().setBody("late").setBodyDelay(2500,TimeUnit.MILLISECONDS)
                else MockResponse().setResponseCode(failureCode)
            val path=request.requestUrl!!.encodedPath
            val body=when{
                path.endsWith("/about")->"""{"user":{"permissionId":"qa-account","emailAddress":"qa@example.invalid"}}"""
                path=="/drive/v3/files"->{
                    val q=request.requestUrl!!.queryParameter("q").orEmpty()
                    val kind=when{q.contains(FavoriteFormat.TAG)->"favorites";q.contains(SiteSettingsFormat.TAG)->"sites";else->"bookmarks"}
                    JSONObject().put("files",JSONArray().put(JSONObject().put("id",kind).put("appProperties",JSONObject().put("device",device)))).toString()
                }
                request.method=="PATCH"->{val id=path.substringAfterLast('/');snapshots[id]=request.body.readUtf8();uploadPaths.add(path);patches.incrementAndGet();"""{"id":"$id"}"""}
                else->snapshots[path.substringAfterLast('/')]?:error("Unexpected fixture request")
            }
            return MockResponse().setBody(body)
        }}
        server.start()
        val endpoint=server.url("/").toString()
        ui.runOnIdle{
            ui.activity.bookmarkSync=BookmarkSync(ui.activity,store){token->BookmarkDrive(token,DriveTransport(DriveTransport.client().newBuilder().readTimeout(1000,TimeUnit.MILLISECONDS).build()){0},endpoint)}
        }
    }
    @After fun cleanup(){ui.runOnIdle{sync.disconnect();store.replace(emptyList());store.accountId="";store.accountLabel="";store.lastSync=0;c.sheet=""};server.shutdown()}
    private fun start(){ui.runOnIdle{sync.syncAuthorized("qa-fixture-token");c.sheet="sync"}}
    private fun finished(){ui.waitUntil(15000){!sync.busy}}
    @Test fun aRealReadTimeoutRetriesThenPreservesDataAndClearsTheWarning(){
        ui.runOnIdle{store.add("https://example.com/sync-qa","Keep this bookmark")}
        slow=true;failures.set(1);start();finished()
        assertTrue(sync.status+": "+sync.details,sync.connected);assertTrue(store.lastSync>123);assertEquals("",sync.details)
        assertEquals(store.all(),BookmarkFormat.readSnapshot(snapshots.getValue("bookmarks")))
        assertEquals("Unexpected uploads: $uploadPaths",1,patches.get())
        val before=server.requestCount
        ui.runOnIdle{sync.resume()};ui.waitForIdle();assertEquals(before,server.requestCount)
        start();finished();assertEquals("Unchanged snapshots must not be uploaded again",1,patches.get())
    }
    @Test fun exhaustedTimeoutKeepsLastSuccessAndReadableStatusThenRecovers(){
        ui.runOnIdle{store.add("https://example.com/kept","Still local");ui.activity.applyAppearance("dark")}
        slow=true;failures.set(3);start();finished()
        assertEquals(123L,store.lastSync);assertEquals(1,store.visible().size);assertFalse(sync.requiresAttention)
        ui.onNodeWithTag("sync-status").assertTextEquals("連線較慢，尚未完成同步")
        ui.onNodeWithTag("sync-details").assertTextContains("本機資料已保留",substring=true)
        assertFalse(sync.details.contains("java."));PreviewFixture(ui).screenshot("sync-timeout-dark")
        ui.runOnIdle{ui.activity.applyAppearance("light")};ui.waitForIdle();PreviewFixture(ui).screenshot("sync-timeout-light")
        start();finished();assertTrue(store.lastSync>123);assertEquals("",sync.details);assertFalse(sync.requiresAttention)
    }
    @Test fun expiredAuthorizationIsActionableAndDoesNotPretendToHaveSynced(){
        failureCode=401;failures.set(1);start();finished()
        assertEquals(1,server.requestCount);assertEquals(123L,store.lastSync);assertTrue(sync.requiresAttention)
        ui.onNodeWithTag("sync-status").assertTextEquals("需要重新確認 Google 授權")
        ui.runOnIdle{sync.disconnect()};assertEquals("",sync.details);assertFalse(sync.requiresAttention)
    }
    @Test fun anotherAccountNeverReceivesLocalData(){
        ui.runOnIdle{store.accountId="different-account";store.add("https://example.com/private","Keep separate")}
        start();finished();assertTrue(sync.requiresAttention);assertEquals(123L,store.lastSync)
        assertEquals(1,server.requestCount);assertEquals(0,patches.get());assertEquals(1,store.visible().size)
    }
}
