package tw.techtarian.browser

import android.Manifest
import android.net.Uri
import android.webkit.PermissionRequest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebsitePermissionsTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val permissions get()=ui.activity.websitePermissions
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var server:MockWebServer
    private val html="""<html><head><title>Website media QA</title><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body><h1>Website media QA</h1><video id="preview" autoplay muted playsinline></video>
        <input id="photo" type="file" accept="image/*" capture="environment"></body></html>"""
    @Before fun setup(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        PreviewFixture(ui).reset()
        ui.runOnIdle{permissions.decisions.clearRegular();permissions.clearPrivate();c.notice=""}
        server=MockWebServer().apply{dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest)=MockResponse().setHeader("Content-Type","text/html; charset=utf-8").setBody(html)};start()}
    }
    @After fun cleanup(){
        ui.runOnIdle{c.prompts.cancel();permissions.decisions.clearRegular();c.closePrivateTabs();c.sheet=""}
        server.shutdown()
    }
    private fun eval(tab:BrowserTab,code:String):String{
        val done=CountDownLatch(1);var result=""
        ui.runOnUiThread{tab.web.evaluateJavascript(code){result=it;done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result
    }
    private fun load(private:Boolean=false):BrowserTab{
        var tab:BrowserTab?=null
        val url=server.url("/media").toString()
        ui.runOnIdle{tab=c.newTab(url,incognito=private)}
        ui.waitUntil(15000){tab!!.title=="Website media QA"&&tab!!.pendingUrl.isEmpty()}
        return tab!!
    }
    private fun requestMedia(tab:BrowserTab,options:String="{audio:true,video:true}"){
        eval(tab,"window.mediaResult=null;navigator.mediaDevices.getUserMedia($options).then(s=>{window.mediaStream=s;document.getElementById('preview').srcObject=s;window.mediaResult={audio:s.getAudioTracks().length,video:s.getVideoTracks().length}}).catch(e=>window.mediaResult={error:e.name});true")
    }
    private fun result(tab:BrowserTab):JSONObject{
        ui.waitUntil(20000){eval(tab,"window.mediaResult")!="null"}
        return JSONObject(eval(tab,"window.mediaResult"))
    }
    private fun allowAndroidDialogs(){
        repeat(3){
            val button=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),4000)
                ?:device.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_one_time_button")))
            button?.click()
        }
    }
    @Test fun cameraAndMicrophoneReachTheWebsiteAndRecordRealMediaAfterBothPermissions(){
        val tab=load()
        requestMedia(tab)
        ui.waitUntil(10000){c.prompts.current!=null}
        ui.onNodeWithText("相機",substring=true).assertExists()
        PreviewFixture(ui).screenshot("website-media-permission")
        ui.onNodeWithText("允許").performClick()
        allowAndroidDialogs()
        val value=result(tab)
        assertFalse(value.toString(),value.has("error"));assertEquals(1,value.getInt("audio"));assertEquals(1,value.getInt("video"))
        eval(tab,"window.recordedBytes=null;const chunks=[];const recorder=new MediaRecorder(window.mediaStream);recorder.ondataavailable=e=>chunks.push(e.data);recorder.onstop=()=>{window.recordedBytes=new Blob(chunks).size;window.mediaStream.getTracks().forEach(t=>t.stop())};recorder.start();setTimeout(()=>recorder.stop(),1200);true")
        ui.waitUntil(15000){eval(tab,"window.recordedBytes")!="null"}
        assertTrue("Recorded audio/video must contain bytes",eval(tab,"window.recordedBytes").toLong()>0)
        assertEquals(LocationChoice.ALLOW,permissions.choice(tab,WebsiteResource.CAMERA))
        assertEquals(LocationChoice.ALLOW,permissions.choice(tab,WebsiteResource.MICROPHONE))
        val generation=tab.navigationGeneration
        ui.runOnIdle{c.sheet="connection"}
        ui.onNodeWithTag("website-permission:CAMERA").performScrollTo().performClick()
        ui.onNodeWithTag("website-permission:CAMERA:BLOCK").performClick()
        ui.runOnIdle{c.sheet=""}
        ui.waitUntil(10000){tab.navigationGeneration>generation&&tab.pendingUrl.isEmpty()}
        requestMedia(tab,"{video:true}")
        assertEquals("NotAllowedError",result(tab).getString("error"));assertNull(c.prompts.current)
    }
    @Test fun refusingCameraIsRememberedWithoutRepeatingTheUnsupportedToast(){
        val tab=load()
        requestMedia(tab,"{video:true}")
        ui.waitUntil(10000){c.prompts.current!=null}
        ui.onNodeWithText("封鎖").performClick()
        assertEquals("NotAllowedError",result(tab).getString("error"))
        requestMedia(tab,"{video:true}")
        assertEquals("NotAllowedError",result(tab).getString("error"))
        assertNull(c.prompts.current);assertEquals("",c.notice)
    }
    private class Request(private val rawOrigin:String,private val requested:Array<String>):PermissionRequest(){
        var granted:List<String>?=null;var denied=false
        override fun getOrigin()=Uri.parse(rawOrigin)
        override fun getResources()=requested
        override fun grant(values:Array<String>){granted=values.toList()}
        override fun deny(){denied=true}
    }
    @Test fun protectedMediaIsNoLongerMistakenForCameraAndUnknownResourcesAreNeverGranted(){
        val tab=load();val request=Request(LocationOrigin.of(tab.url)!!,arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,"future-sensitive-resource"))
        ui.runOnIdle{tab.web.webChromeClient!!.onPermissionRequest(request)}
        assertEquals(listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),request.granted)
        assertNull(c.prompts.current);assertEquals("",c.notice)
    }
    @Test fun midiCanBeAllowedAndPendingCaptureCannotFollowNavigation(){
        val tab=load();val origin=LocationOrigin.of(tab.url)!!
        val midi=Request(origin,arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX))
        ui.runOnIdle{permissions.request(tab,midi)}
        ui.onNodeWithText("允許").performClick()
        assertEquals(listOf(PermissionRequest.RESOURCE_MIDI_SYSEX),midi.granted)
        val camera=Request(origin,arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        ui.runOnIdle{permissions.request(tab,camera)}
        assertNotNull(c.prompts.current)
        ui.runOnIdle{c.newTab()}
        assertTrue(camera.denied);assertNull(c.prompts.current)
        assertEquals(LocationChoice.ASK,permissions.decisions.get(origin,WebsiteResource.CAMERA,false))
    }
    @Test fun privateGrantsAndBlocksNeverLeakIntoRegularOrLaterPrivateSessions(){
        val tab=load(private=true);val origin=LocationOrigin.of(tab.url)!!
        ui.runOnIdle{permissions.decisions.set(origin,WebsiteResource.CAMERA,true,LocationChoice.ALLOW)}
        assertEquals(LocationChoice.ASK,permissions.decisions.get(origin,WebsiteResource.CAMERA,false))
        assertEquals(LocationChoice.ASK,WebsitePermissionDecisions(ui.activity).get(origin,WebsiteResource.CAMERA,true))
        ui.runOnIdle{c.closePrivateTabs()}
        assertEquals(LocationChoice.ASK,permissions.decisions.get(origin,WebsiteResource.CAMERA,true))
    }
    @Test fun platformCapabilityAuditUsesTheRunningWebView(){
        val tab=load()
        val value=eval(tab,"({secure:isSecureContext,getUserMedia:typeof navigator.mediaDevices?.getUserMedia,mediaRecorder:typeof MediaRecorder,webRtc:typeof RTCPeerConnection,protectedMedia:typeof navigator.requestMediaKeySystemAccess,midi:typeof navigator.requestMIDIAccess,notifications:typeof Notification,push:typeof PushManager,screenSharing:typeof navigator.mediaDevices?.getDisplayMedia,bluetooth:typeof navigator.bluetooth,usb:typeof navigator.usb,passkeys:typeof PublicKeyCredential,serviceWorker:typeof navigator.serviceWorker})")
        android.util.Log.i("WebsiteCapabilityAudit",value)
        assertTrue(JSONObject(value).getBoolean("secure"))
        assertEquals("function",JSONObject(value).getString("getUserMedia"))
    }
}
