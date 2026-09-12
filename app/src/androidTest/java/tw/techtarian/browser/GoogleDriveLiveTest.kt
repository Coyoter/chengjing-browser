package tw.techtarian.browser

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in live test: synthetic bookmarks only; no credentials in logs. Run explicitly on the QA emulator. */
class GoogleDriveLiveTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val activity get()=ui.activity
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun waitForSync(previous:Long){
        val deadline=System.currentTimeMillis()+75000
        while(System.currentTimeMillis()<deadline){
            if(activity.bookmarkSync.connected && !activity.bookmarkSync.busy && activity.controller.store.bookmarkStore.lastSync>previous)return
            Thread.sleep(200)
        }
        error("Live sync did not finish: ${activity.bookmarkSync.status}")
    }
    private fun shot(name:String){
        ui.waitForIdle()
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-Google-Live")}
        val uri=activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun nativeConsentDriveReadbackMergeAndDeletion(){
        org.junit.Assume.assumeTrue("Live Google test is opt-in", InstrumentationRegistry.getArguments().getString("liveGoogle")=="true")
        val store=activity.controller.store.bookmarkStore
        val initial=store.lastSync
        main{activity.controller.sheet="sync"}
        if(!activity.bookmarkSync.connected){
            ui.onNodeWithText("使用 Google 帳戶連結").performClick()
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.wait(Until.findObject(By.text("coyoter@coyoter.com")),15000)?.click()
            val allow=device.wait(Until.findObject(By.text("Allow")),15000)
            if(allow!=null)allow.click()
        } else main{activity.bookmarkSync.authorize(false)}
        waitForSync(initial)
        val original=store.visible().map{it.id}.toSet()
        shot("01-connected")
        val stamp=UUID.randomUUID().toString()
        val url="https://example.com/chengjing-qa/$stamp"
        val primary=Bookmark(Bookmark.id(url,"QA"),url,"澄境同步驗證・本機","QA",System.currentTimeMillis())
        var previous=store.lastSync
        main{store.importRows(listOf(primary))}
        waitForSync(previous)
        assertTrue(store.visible().any{it.id==primary.id})
        Log.i("CJLiveSync","native add -> upload -> readback verified")
        // A second logical device writes its OWN snapshot through the exact production Drive transport.
        val authorization=Tasks.await(Identity.getAuthorizationClient(activity).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(BookmarkSync.SCOPE))).build()),30,TimeUnit.SECONDS)
        check(!authorization.hasResolution() && !authorization.accessToken.isNullOrBlank())
        val drive=BookmarkDrive(authorization.accessToken!!)
        val peerUrl="https://example.org/chengjing-qa/$stamp"
        val peer=Bookmark(Bookmark.id(peerUrl,"QA"),peerUrl,"澄境同步驗證・另一裝置","QA",System.currentTimeMillis())
        val peerFile=drive.write("qa-peer-$stamp",null,listOf(peer))
        assertEquals(listOf(peer),drive.read(peerFile))
        previous=store.lastSync
        main{activity.bookmarkSync.authorize(false)}
        waitForSync(previous)
        assertTrue(store.visible().any{it.id==peer.id});assertTrue(store.visible().any{it.id==primary.id})
        Log.i("CJLiveSync","independent device snapshot -> native merge verified")
        shot("02-two-device-merge")
        drive.write("qa-peer-$stamp",peerFile,listOf(peer.copy(deleted=true,updated=System.currentTimeMillis()+100)))
        previous=store.lastSync
        main{store.remove(primary.id)}
        waitForSync(previous)
        assertFalse(store.visible().any{it.id in setOf(peer.id,primary.id)})
        assertEquals(original,store.visible().map{it.id}.toSet())
        Log.i("CJLiveSync","both synthetic records removed; original visible bookmarks preserved")
        val persisted=store.lastSync
        ui.activityRule.scenario.recreate()
        waitForSync(persisted)
        assertEquals(original,activity.controller.store.bookmarkStore.visible().map{it.id}.toSet())
        main{activity.controller.sheet="sync"}
        shot("03-reopened-and-synced")
        Log.i("CJLiveSync","activity recreation -> silent authorization -> Drive readback verified")
    }
}
