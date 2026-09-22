package tw.techtarian.browser

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ImageActionsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ui.activity.controller
    private val device get()=UiDevice.getInstance(instrumentation)
    private val files get()=ui.activity.imageActions.files
    private val receiver=ComponentName("tw.techtarian.browser.smoketests","tw.techtarian.browser.smoketests.ImageReceiverActivity")
    private lateinit var server:ImageServer
    private val saved=mutableListOf<Uri>()
    @Before fun before(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        ui.runOnIdle{ui.activity.bookmarkSync.disconnect();c.stopEye();c.sheet="";c.imagePreview=null;c.tabs.map{it.id}.forEach{c.closeTab(it)}}
        server=ImageServer()
    }
    @After fun after(){saved.forEach{ui.activity.contentResolver.delete(it,null,null)};server.close();ui.runOnIdle{c.imagePreview=null;c.tabs.map{it.id}.forEach{c.closeTab(it)}}}
    private fun bytes(format:Bitmap.CompressFormat=Bitmap.CompressFormat.PNG):ByteArray {
        val bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE);bitmap.setPixel(40,30,Color.YELLOW)
        return ByteArrayOutputStream().also{bitmap.compress(format,95,it);bitmap.recycle()}.toByteArray()
    }
    private fun data(bytes:ByteArray)="data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun eval(code:String):String {
        val latch=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{c.active!!.web.evaluateJavascript(code){result.set(it);latch.countDown()}}
        assertTrue(latch.await(8,TimeUnit.SECONDS));return result.get()
    }
    private fun fixture(src:String,private:Boolean=false){
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>圖片測試</title></head><body style='margin:24px'><a href='https://example.com/article'><img id='picture' alt='QA 測試圖片' src='${src.replace("'","&#39;")}' width='180' height='180'></a></body></html>"
        ui.runOnIdle{c.newTab(incognito=private)!!.web.loadDataWithBaseURL(server.url("/page"),html,"text/html","UTF-8",null)}
        ui.waitUntil(15000){eval("!!document.querySelector('#picture')?.complete && document.querySelector('#picture').naturalWidth>0")=="true"}
        ui.onNodeWithTag("web-content").assertIsDisplayed()
    }
    private fun menu(selector:String="#picture") {
        ui.waitForIdle()
        val drawn=CountDownLatch(1)
        ui.runOnUiThread{c.active!!.web.postVisualStateCallback(1,object:android.webkit.WebView.VisualStateCallback(){override fun onComplete(id:Long){drawn.countDown()}})}
        assertTrue("WebView has not drawn the image",drawn.await(8,TimeUnit.SECONDS))
        val p=JSONObject(eval("(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val origin=IntArray(2);ui.runOnUiThread{c.active!!.web.getLocationOnScreen(origin)}
        val x=origin[0]+(p.getDouble("x")*p.getDouble("dpr")).toFloat();val y=origin[1]+(p.getDouble("y")*p.getDouble("dpr")).toFloat()
        val down=SystemClock.uptimeMillis()
        fun send(action:Int){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{instrumentation.sendPointerSync(e)}finally{e.recycle()}}
        send(MotionEvent.ACTION_DOWN);SystemClock.sleep(850);send(MotionEvent.ACTION_UP)
        val item=device.wait(Until.findObject(By.text("下載圖片")),5000)
        if(item==null){device.executeShellCommand("mkdir -p /data/local/tmp/chengjing-ui");device.executeShellCommand("screencap -p /data/local/tmp/chengjing-ui/image-menu-failure.png")}
        assertNotNull("No image menu at $x,$y (web origin ${origin.toList()})",item)
    }
    private fun click(label:String){val node=device.wait(Until.findObject(By.text(label)),10000);assertNotNull("Missing $label",node);node!!.click()}
    private fun expected(raw:ByteArray)="image-received:image/png:${raw.size}:"+MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}
    private fun checkReceiver(raw:ByteArray){assertNotNull(device.wait(Until.findObject(By.desc(expected(raw))),10000));device.pressBack();ui.waitForIdle()}

    @Test fun responseBytesDetermineFileNameMimeAndSavedContents()=runBlocking {
        for((format,expected) in listOf(Bitmap.CompressFormat.PNG to ImageFormat.PNG,Bitmap.CompressFormat.JPEG to ImageFormat.JPEG,Bitmap.CompressFormat.WEBP_LOSSLESS to ImageFormat.WEBP)){
            val raw=bytes(format);server.body=raw
            val asset=files.fetchHttp(server.url("/image.bin"),server.url("/page?private=1"),"QA",false){null}
            assertEquals(expected,asset.format);assertEquals(expected.extension,asset.file.extension)
            assertArrayEquals(raw,asset.file.readBytes())
            val row=SavedImageDownloads(ui.activity).save(asset);val uri=Uri.parse(row.contentUri);saved.add(uri)
            assertEquals(expected.mime,ui.activity.contentResolver.getType(uri))
            assertArrayEquals(raw,ui.activity.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
            ui.activity.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.IS_PENDING),null,null,null)!!.use{cursor->
                assertTrue(cursor.moveToFirst());assertTrue(cursor.getString(0).endsWith(".${expected.extension}"));assertEquals(0,cursor.getInt(1))
            }
        }
    }
    @Test fun redirectsRecheckCookiesAndNeverSaveHtmlAsAnImage()=runBlocking {
        server.body=bytes()
        files.fetchHttp(server.url("/redirect"),server.url("/page?secret=fixture"),"QA",false){if(Uri.parse(it).host=="127.0.0.1")"qa_only=1"else null}
        val redirected=server.requests.last{it.startsWith("GET /image")}
        assertFalse(redirected.contains("qa_only"));assertFalse(redirected.contains("secret=fixture"))
        server.body="<html>Not an image</html>".toByteArray()
        assertTrue(runCatching{files.fetchHttp(server.url("/image"),server.url("/page"),"QA",false){null}}.isFailure)
        server.body=byteArrayOf(137.toByte(),80,78,71,13,10,26,10)
        assertTrue(runCatching{files.fetchHttp(server.url("/image"),server.url("/page"),"QA",false){null}}.isFailure)
    }
    @Test fun imageCacheIsBoundedAndClearingItKeepsDownloadedFiles()=runBlocking {
        val directory=java.io.File(ui.activity.cacheDir,"shared-images")
        val old=java.io.File(directory,"expired.png").apply{writeBytes(bytes());setLastModified(System.currentTimeMillis()-90_000_000)}
        repeat(5){index->java.io.File(directory,"capacity-$index.png").apply{
            java.io.RandomAccessFile(this,"rw").use{it.setLength(32L*1024*1024)}
            setLastModified(System.currentTimeMillis()-60_000L*(index+1))
        }}
        ImageFiles(ui.activity).close()
        assertFalse(old.exists())
        assertTrue(directory.listFiles()!!.sumOf{it.length()}<=128L*1024*1024)
        val raw=bytes();val asset=files.fromBytes(raw,"","",false)
        val uri=Uri.parse(SavedImageDownloads(ui.activity).save(asset).contentUri);saved.add(uri)
        files.clearShared();assertFalse(asset.file.exists())
        assertArrayEquals(raw,ui.activity.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
    }
    @Test fun dataAndBlobImagesKeepOriginalBytesAndUseTemporaryImageTabs() {
        val raw=bytes();fixture(data(raw))
        val blob=JSONTokener(eval("(()=>{const b=Uint8Array.from(atob('${Base64.encodeToString(raw,Base64.NO_WRAP)}'),c=>c.charCodeAt(0));return URL.createObjectURL(new Blob([b],{type:'image/png'}))})()")).nextValue() as String
        eval("document.querySelector('#picture').src=${JSONObject.quote(blob)}")
        ui.waitUntil(5000){eval("document.querySelector('#picture').complete")=="true"}
        val original=c.active!!.id
        menu();click("在新分頁開啟圖片")
        ui.waitUntil(10000){c.active?.imageContent!=null}
        ui.runOnIdle{assertNotEquals(original,c.activeId);assertArrayEquals(raw,c.active!!.imageContent!!.file.readBytes())}
        ui.onNodeWithTag("image-preview-content").assertExists()
        ui.onNodeWithContentDescription("關閉圖片預覽").performClick()
        ui.runOnIdle{assertEquals(original,c.activeId)}
        assertArrayEquals(raw,PageImageReader.dataBytes(data(raw)))
        val encoded="data:image/png,"+raw.joinToString(""){"%%%02X".format(it)}
        assertArrayEquals(raw,PageImageReader.dataBytes(encoded))
    }
    @Test fun menuPreviewCopyAndShareTransferActualImageToAnotherApp() {
        val raw=bytes();fixture(data(raw));menu()
        for(label in listOf("在新分頁開啟圖片","預覽圖片","複製圖片","下載圖片","分享圖片"))assertTrue(device.hasObject(By.text(label)))
        assertFalse(device.hasObject(By.textContains("Lens")))
        click("預覽圖片");ui.waitUntil(10000){c.imagePreview!=null}
        ui.onNodeWithTag("image-preview-content").assertExists()
        ui.onNodeWithContentDescription("關閉圖片預覽").performClick()
        val clipboard=ui.activity.getSystemService(android.content.ClipboardManager::class.java)
        ui.runOnIdle{clipboard.clearPrimaryClip()}
        menu();click("複製圖片")
        ui.waitUntil(10000){var copied=false;ui.runOnUiThread{copied=clipboard.primaryClip?.getItemAt(0)?.uri?.authority=="${ui.activity.packageName}.images"};copied}
        ui.runOnIdle{ui.activity.startActivity(Intent().setComponent(receiver))}
        click("Paste QA image");checkReceiver(raw)
        menu();click("分享圖片");click("QA 圖片接收器");checkReceiver(raw)
    }
    @Test fun privatePreviewStaysPrivateAndCancelledExportWritesNothing() {
        val raw=bytes();fixture(data(raw),true);menu();click("預覽圖片")
        ui.waitUntil(10000){c.imagePreview!=null}
        val asset=c.imagePreview!!;assertTrue(asset.private)
        assertTrue(runCatching{asset.uri(ui.activity)}.isFailure)
        ui.onNodeWithText("下載圖片").performClick();click("取消")
        assertFalse(c.notice.contains("圖片已下載"))
        ui.onNodeWithContentDescription("關閉圖片預覽").performClick()
        ui.runOnIdle{c.closeTab(c.activeId)}
        assertFalse(asset.file.exists())
    }
    @Test fun longPressDownloadCreatesAUsableEntryAndHttpImageNewTab() {
        val raw=bytes();server.body=raw;val url=server.url("/image")
        fixture(url);val before=SavedImageDownloads(ui.activity).list().map{it.id}.toSet()
        menu();click("下載圖片")
        ui.waitUntil(15000){SavedImageDownloads(ui.activity).list().any{it.id !in before}}
        val row=SavedImageDownloads(ui.activity).list().first{it.id !in before};val uri=Uri.parse(row.contentUri);saved.add(uri)
        assertTrue(row.title.endsWith(".png"));assertArrayEquals(raw,ui.activity.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
        val original=c.activeId;menu();click("在新分頁開啟圖片")
        ui.waitUntil(10000){c.activeId!=original&&c.active?.url==url}
        ui.runOnIdle{assertNull(c.active!!.imageContent)}
    }
    @Test fun currentSrcUsesDisplayedResponsiveImageInsteadOfAnchor() {
        val raw=bytes();fixture(data(raw));server.body=raw
        val responsive=server.url("/responsive")
        eval("document.querySelector('#picture').srcset='${responsive} 1x, ${responsive} 4x'")
        ui.waitUntil(5000){eval("document.querySelector('#picture').currentSrc===${JSONObject.quote(responsive)}")=="true"}
        val downloaded=AtomicReference<String?>(null)
        ui.runOnIdle{PageContextMenu(c,c.active!!){url,_,_->downloaded.set(url)}.install()}
        menu();click("下載圖片");ui.waitUntil(5000){downloaded.get()!=null};assertEquals(responsive,downloaded.get())
    }
    /** Optional live QA only; CI never depends on a public search page. */
    @Test fun liveGoogleImageDownload() {
        val url=InstrumentationRegistry.getArguments().getString("liveGoogleUrl")
        Assume.assumeTrue(!url.isNullOrEmpty())
        ui.runOnIdle{c.newTab(url!!)}
        val selector="img[src*='play-lh.googleusercontent.com']"
        ui.waitUntil(60000){eval("Array.from(document.querySelectorAll(${JSONObject.quote(selector)})).some(i=>i.naturalWidth>0&&i.getBoundingClientRect().width>150)")=="true"}
        eval("Array.from(document.querySelectorAll(${JSONObject.quote(selector)})).find(i=>i.naturalWidth>0&&i.getBoundingClientRect().width>150).id='live-target'")
        val before=SavedImageDownloads(ui.activity).list().map{it.id}.toSet()
        menu("#live-target");click("下載圖片")
        ui.waitUntil(45000){SavedImageDownloads(ui.activity).list().any{it.id !in before}}
        val row=SavedImageDownloads(ui.activity).list().first{it.id !in before}
        val raw=ui.activity.contentResolver.openInputStream(Uri.parse(row.contentUri))!!.use{it.readBytes()}
        assertEquals(ImageFormat.sniff(raw)!!.mime,row.mime);assertFalse(row.title.endsWith(".bin"))
        assertTrue(raw.size>1000)
        // Keep this explicitly requested public image for offline file/visual verification.
        println("LIVE_IMAGE_RESULT ${row.title} ${row.mime} ${raw.size} ${row.contentUri}")
    }
    private inner class ImageServer:AutoCloseable {
        private val socket=ServerSocket(0)
        @Volatile var body=bytes()
        val requests=java.util.Collections.synchronizedList(mutableListOf<String>())
        private val worker=Thread{
            while(!socket.isClosed)try{socket.accept().use{client->
                val reader=client.getInputStream().bufferedReader();val first=reader.readLine().orEmpty();val headers=mutableListOf(first)
                while(true){val line=reader.readLine();if(line.isNullOrEmpty())break;headers.add(line)}
                requests.add(headers.joinToString("\n"))
                val redirect=first.contains("/redirect");val data=if(redirect)byteArrayOf()else body
                val header=if(redirect)"HTTP/1.1 302 Found\r\nLocation: http://localhost:${socket.localPort}/image\r\n"else if(first.contains("/image.bin"))"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=photo.bin\r\n"else "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n"
                client.getOutputStream().write((header+"Content-Length: ${data.size}\r\nConnection: close\r\n\r\n").toByteArray());client.getOutputStream().write(data)
            }}catch(_:java.io.IOException){if(socket.isClosed)break}
        }.apply{isDaemon=true;start()}
        fun url(path:String)="http://127.0.0.1:${socket.localPort}$path"
        override fun close(){socket.close();worker.join(1000)}
    }
}
