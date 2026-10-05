package tw.techtarian.browser

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SystemIntegrationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private var oldService:String?=null
    private var changedService=false
    private var server:okhttp3.mockwebserver.MockWebServer?=null
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{String(it.readBytes()).trim()}
    private fun eval(code:String):String {val latch=CountDownLatch(1);var result="";ui.runOnIdle{c.active!!.web.evaluateJavascript(code){result=it;latch.countDown()}};assertTrue(latch.await(5,TimeUnit.SECONDS));return result}
    @Before fun guard(){Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))}
    @After fun restore(){
        server?.shutdown()
        if(changedService){if(oldService==null||oldService=="null")shell("settings delete secure autofill_service")else shell("settings put secure autofill_service $oldService")}
        ui.runOnIdle{c.sheet="";c.closePrivateTabs()}
    }
    @Test fun manifestAndSystemIntentsAllowUserControlledPackageInstallation(){
        @Suppress("DEPRECATION")
        val permissions=ui.activity.packageManager.getPackageInfo(ui.activity.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"));assertFalse(permissions.contains("android.permission.INSTALL_PACKAGES"))
        val settings=PackageDownloads.settingsIntent(ui.activity)
        assertEquals("package:${ui.activity.packageName}",settings.data.toString())
        val uri=Uri.parse("content://downloads/my_downloads/123")
        val install=PackageDownloads.installerIntent(uri)
        assertEquals(uri,install.data);assertEquals(PackageDownloads.MIME,install.type)
        assertTrue(install.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        assertEquals(uri,install.clipData!!.getItemAt(0).uri)
        assertEquals("package:${ui.activity.packageName}",SystemAutofill.settingsIntent(ui.activity).data.toString())
        ui.runOnIdle{c.sheet="settings"}
        ui.onNodeWithText("瀏覽").performClick()
        ui.onNodeWithTag("system-autofill-settings").performScrollTo().assertIsDisplayed()
        shell("mkdir -p /data/local/tmp/chengjing-system-qa")
        shell("screencap -p /data/local/tmp/chengjing-system-qa/settings-light.png")
        ui.runOnIdle{ui.activity.applyAppearance("dark")}
        ui.waitForIdle();shell("screencap -p /data/local/tmp/chengjing-system-qa/settings-dark.png")
        ui.runOnIdle{ui.activity.applyAppearance("system")}

    }
    @Test fun userSelectedDownloadedApkReachesTheAndroidInstaller(){
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        val path=shell("pm path tw.techtarian.browser.smoketests").lineSequence().first().removePrefix("package:")
        require(path.startsWith("/data/app/")&&Regex("[A-Za-z0-9_./+=~:-]+").matches(path))
        val resolver=ui.activity.contentResolver
        val values=android.content.ContentValues().apply{
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"qa-package.apk")
            put(android.provider.MediaStore.Downloads.MIME_TYPE,PackageDownloads.MIME)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH,"Download/ChengJingQA/")
            put(android.provider.MediaStore.Downloads.IS_PENDING,1)
        }
        val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        try{
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $path")).use{input->resolver.openOutputStream(uri)!!.use{input.copyTo(it)}}
            resolver.update(uri,android.content.ContentValues().apply{put(android.provider.MediaStore.Downloads.IS_PENDING,0)},null,null)
            shell("appops set ${ui.activity.packageName} REQUEST_INSTALL_PACKAGES allow")
            ui.runOnIdle{assertTrue(PackageDownloads.allowed(ui.activity));ui.activity.installPackage(DownloadItem(0,"qa-package.apk","",PackageDownloads.MIME,8,0,0,uri.toString()))}
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue("System installer must display the actual package name",device.wait(Until.hasObject(By.text("Browser optimized-build tests")),20000))
            assertNotEquals(ui.activity.packageName,device.currentPackageName)
            device.pressBack()
        }finally{
            resolver.delete(uri,null,null)
        }
    }
    @Test fun normalWebsiteReceivesRealAndroidAutofillWhilePrivateTabsRemainExcluded(){
        oldService=shell("settings get secure autofill_service");changedService=true
        val testPackage=InstrumentationRegistry.getInstrumentation().context.packageName
        shell("settings put secure autofill_service $testPackage/tw.techtarian.browser.FixtureAutofillService")
        ui.activityRule.scenario.recreate()
        val fixture=okhttp3.mockwebserver.MockWebServer().also{server=it}
        fixture.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type","text/html; charset=utf-8").setBody("""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><form><input id="user" name="username" autocomplete="username" style="display:block;margin:30px;height:40px"><input id="pass" name="password" type="password" autocomplete="current-password" style="display:block;margin:30px;height:40px"></form></body></html>"""))
        fixture.start()
        val url=fixture.url("/login").toString()
        ui.runOnIdle{c.sheet="";c.newTab(url,incognito=false)}
        ui.waitUntil(10000){eval("!!document.getElementById('user')")=="true"}
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ui.waitUntil(5000){var ready=false;ui.runOnIdle{ready=c.active!!.web.isAttachedToWindow&&c.active!!.web.width>0};ready}
        android.os.SystemClock.sleep(500)
        val rect=org.json.JSONObject(eval("JSON.stringify((()=>{const r=document.getElementById('user').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})())").let{org.json.JSONArray("[$it]").getString(0)})
        val location=IntArray(2);ui.runOnIdle{c.active!!.web.getLocationOnScreen(location)}
        device.click(location[0]+(rect.getDouble("x")*rect.getDouble("dpr")).toInt(),location[1]+(rect.getDouble("y")*rect.getDouble("dpr")).toInt())
        ui.waitUntil(5000){eval("document.activeElement.id")=="\"user\""}
        val dataset=device.wait(Until.findObject(By.text("QA saved login")),15000)
        if(dataset==null){shell("screencap -p /data/local/tmp/autofill-dataset.png")}
        assertNotNull("Android provider must receive the genuine website domain and form",dataset);dataset!!.click()
        ui.waitUntil(5000){eval("document.getElementById('user').value")=="\"qa@example.invalid\""}
        assertEquals("\"qa-fixture-password\"",eval("document.getElementById('pass').value"))
        ui.runOnIdle{
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES,c.active!!.web.importantForAutofill)
            c.newTab("https://practice.chengjing.invalid/",incognito=true)
            assertTrue(c.active!!.incognito);assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,c.active!!.web.importantForAutofill)
        }
    }
}
