package tw.techtarian.browser

import android.app.DownloadManager
import android.net.Uri
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PageDownloadsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val transfers get()=ui.activity.pageDownloads
    private val catalog get()=SavedImageDownloads(ui.activity)
    private var original:String?=null
    private var before=emptySet<Long>()
    private fun eval(code:String):String {val done=CountDownLatch(1);var result="null";ui.runOnUiThread{c.active!!.web.evaluateJavascript(code){result=it;done.countDown()}};assertTrue(done.await(15,TimeUnit.SECONDS));return result}
    private fun value(code:String)=org.json.JSONTokener(eval(code)).nextValue().toString()
    private fun painted(){val done=CountDownLatch(1);ui.runOnUiThread{c.active!!.web.postVisualStateCallback(1,object:android.webkit.WebView.VisualStateCallback(){override fun onComplete(id:Long){done.countDown()}})};assertTrue(done.await(15,TimeUnit.SECONDS));ui.waitForIdle()}
    @Before fun start(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        PreviewFixture(ui).reset();original=ui.activity.getSharedPreferences("saved-image-downloads-v1",0).getString("items",null);before=catalog.list().map{it.id}.toSet()
        ui.runOnIdle{c.navigate("https://practice.chengjing.invalid/")}
        ui.waitUntil(15000){c.active?.pendingUrl==""&&c.active?.url=="https://practice.chengjing.invalid/"}
    }
    @After fun cleanup(){
        ui.runOnIdle{c.tabs.forEach{transfers.cancelFor(it.id)};c.sheet="";c.closePrivateTabs()}
        catalog.list().filter{it.id !in before&&it.contentUri?.startsWith("content:")==true}.forEach{runCatching{ui.activity.contentResolver.delete(Uri.parse(it.contentUri),null,null)}}
        val prefs=ui.activity.getSharedPreferences("saved-image-downloads-v1",0)
        prefs.edit().apply{if(original==null)remove("items")else putString("items",original)}.commit()
    }
    private fun page(script:String){
        eval("""(()=>{document.body.innerHTML='<a id="fixture-download" style="display:block;padding:30px;font:20px sans-serif">Download fixture</a><video id="fixture-video" controls style="width:300px;height:190px"></video>'; $script; const a=document.getElementById('fixture-download');a.href=window.fixtureUrl;a.download=window.fixtureName;document.getElementById('fixture-video').src=window.fixtureUrl;return true;})()""")
    }
    private fun tapDownload(){
        eval("document.getElementById('fixture-download').scrollIntoView({block:'center'})")
        painted()
        val rect=JSONObject(eval("(()=>{const r=document.getElementById('fixture-download').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val origin=IntArray(2);ui.runOnUiThread{c.active!!.web.getLocationOnScreen(origin)}
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(origin[0]+(rect.getDouble("x")*rect.getDouble("dpr")).toInt(),origin[1]+(rect.getDouble("y")*rect.getDouble("dpr")).toInt())
        try{ui.waitUntil(10000){ui.onAllNodesWithText("下載檔案？").fetchSemanticsNodes().isNotEmpty()}}
        catch(error:Throwable){throw AssertionError("Download did not start: url=${c.active?.url}; blocked=${c.active?.blockedEvents}; link="+eval("document.getElementById('fixture-download').outerHTML")+"; UI="+ui.onRoot().printToString(),error)}
    }
    private fun confirm(){ui.onNodeWithText("下載",substring=false).performClick()}
    private fun finished():DownloadItem {
        ui.waitUntil(180000){catalog.list().any{it.id !in before&&it.status==DownloadManager.STATUS_SUCCESSFUL}||transfers.items.any{it.status==DownloadManager.STATUS_FAILED}}
        assertFalse("Transfer error: ${transfers.items}",transfers.items.any{it.status==DownloadManager.STATUS_FAILED})
        return catalog.list().first{it.id !in before&&it.status==DownloadManager.STATUS_SUCCESSFUL}
    }
    private fun bytes(item:DownloadItem)=ui.activity.contentResolver.openInputStream(Uri.parse(item.contentUri))!!.use{it.readBytes()}
    private fun video()=InstrumentationRegistry.getInstrumentation().context.assets.open("download-video.b64").bufferedReader().use{Base64.decode(it.readText(),Base64.DEFAULT)}
    private fun videoPage(megabytes:Int=0){
        val encoded=Base64.encodeToString(video(),Base64.NO_WRAP)
        page("""const raw=Uint8Array.from(atob('$encoded'),c=>c.charCodeAt(0)),parts=[raw];if($megabytes>0){parts.push(new Uint8Array([0,0,0,0,102,114,101,101]));const zero=new Uint8Array(1024*1024);for(let i=0;i<$megabytes;i++)parts.push(zero)}window.fixtureUrl=URL.createObjectURL(new Blob(parts,{type:'video/mp4'}));window.fixtureName='test-video.mp4';""")
    }
    @Test fun largeBlobVideoIsStreamedAndRemainsPlayable(){
        videoPage(132);val expected=video().size+8L+132L*1024*1024
        tapDownload();confirm()
        ui.runOnIdle{c.sheet="downloads"};ui.onNodeWithTag("download-list").assertExists()
        val item=finished();assertEquals(expected,item.total);assertEquals("video/mp4",item.mime);assertEquals("test-video.mp4",item.title)
        val digest=MessageDigest.getInstance("SHA-256")
        ui.activity.contentResolver.openInputStream(Uri.parse(item.contentUri))!!.use{input->val buffer=ByteArray(64*1024);var size=0L;while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n);size+=n};assertEquals(expected,size)}
        val reference=MessageDigest.getInstance("SHA-256");reference.update(video());reference.update(byteArrayOf(0,0,0,0,102,114,101,101));repeat(132){reference.update(ByteArray(1024*1024))};assertArrayEquals(reference.digest(),digest.digest())
        val metadata=MediaMetadataRetriever();try{metadata.setDataSource(ui.activity,Uri.parse(item.contentUri));assertEquals("1000",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))}finally{metadata.release()}
    }
    @Test fun dataPdfAndUtf8TextKeepTheirActualBytes(){
        val pdf="%PDF-1.7\nfixture\n%%EOF".toByteArray()
        page("window.fixtureUrl='data:application/pdf;base64,${Base64.encodeToString(pdf,Base64.NO_WRAP)}';window.fixtureName='report.pdf';")
        tapDownload();confirm();val item=finished();assertArrayEquals(pdf,bytes(item));assertEquals("application/pdf",item.mime);assertTrue(item.title.endsWith(".pdf"))
    }
    @Test fun utf8CsvDownloadPreservesTextAndPlusCharacters(){
        page("window.fixtureUrl='data:text/csv;charset=utf-8,%E5%90%8D%E7%A8%B1%2C%E5%80%BC%0A%E6%B8%AC%E8%A9%A6%2CA%2BB';window.fixtureName='report.csv';")
        tapDownload();confirm();val item=finished();assertEquals("text/csv",item.mime);assertEquals("名稱,值\n測試,A+B",String(bytes(item),Charsets.UTF_8))
    }
    @Test fun nativeVideoOverflowDownloadActuallyReachesTheNewDownloader(){
        videoPage();ui.waitUntil(15000){eval("document.getElementById('fixture-video').readyState>=1")=="true"};painted()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val more=device.wait(Until.findObject(By.desc(java.util.regex.Pattern.compile("(?i).*(more|更多).*"))),3000)
            ?:device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i).*(more|更多).*"))),3000)
            // This WebView exposes its overflow as the video's only unnamed, enabled button.
            ?:device.findObject(By.res("fixture-video"))?.findObjects(By.clazz("android.widget.Button").clickable(true).enabled(true))?.singleOrNull{it.contentDescription.isNullOrEmpty()}
        assertNotNull("Native video overflow must be available",more);more!!.click()
        val download=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)download|下載"))),2000)
            ?:device.wait(Until.findObject(By.desc(java.util.regex.Pattern.compile("(?i)download( media)?|下載(媒體)?"))),2000)
        assertNotNull("Native video menu must contain Download",download);download!!.click()
        ui.waitUntil(10000){ui.onAllNodesWithText("下載檔案？").fetchSemanticsNodes().isNotEmpty()};confirm()
        val item=finished();assertEquals("video/mp4",item.mime);assertArrayEquals(video(),bytes(item))
    }
    @Test fun aGenericBlobGetsItsRealFormatInsteadOfBin(){
        page("window.fixtureUrl=URL.createObjectURL(new Blob(['%PDF-1.7\\nfixture\\n%%EOF'],{type:'application/octet-stream'}));window.fixtureName='report.bin';")
        tapDownload();confirm();val item=finished();assertEquals("report.pdf",item.title);assertEquals("application/pdf",item.mime);assertTrue(String(bytes(item)).startsWith("%PDF-"))
    }
    @Test fun cancelRemovesThePendingFile(){
        videoPage(132);tapDownload();confirm()
        ui.waitUntil(15000){transfers.items.any{it.done>0&&it.done<it.total}}
        ui.runOnIdle{transfers.cancel(transfers.items.first().id)}
        ui.waitUntil(10000){transfers.items.any{it.status==DownloadManager.STATUS_FAILED}}
        ui.waitUntil(10000){ui.activity.getSharedPreferences("page-download-pending-v1",0).all.isEmpty()}
        assertTrue(catalog.list().none{it.id !in before});assertTrue(transfers.items.first().detail.startsWith("已取消"))
    }
    @Test fun closingTheOriginTabStopsItsBlobTransfer(){
        videoPage(132);tapDownload();confirm();ui.waitUntil(15000){transfers.items.any{it.done>0}}
        ui.runOnIdle{c.closeTab(c.activeId)}
        ui.waitUntil(10000){ui.activity.getSharedPreferences("page-download-pending-v1",0).all.isEmpty()}
        assertTrue(catalog.list().none{it.id !in before})
    }
    @Test fun privateDownloadNeedsOneConfirmationAndCancelWritesNothing(){
        ui.runOnIdle{c.newTab("https://practice.chengjing.invalid/",incognito=true)}
        ui.waitUntil(15000){c.active?.pendingUrl==""};videoPage();tapDownload()
        ui.onNodeWithText("不會隨無痕分頁清除",substring=true).assertExists();ui.onNodeWithText("取消").performClick()
        assertTrue(transfers.items.isEmpty());assertTrue(catalog.list().none{it.id !in before})
        tapDownload();confirm();val item=finished();assertEquals("",item.source);assertArrayEquals(video(),bytes(item))
    }
    @Test fun revokedBlobFailsWithoutPublishingAnEmptyFile(){
        videoPage();val url=value("window.fixtureUrl");eval("URL.revokeObjectURL(window.fixtureUrl)")
        ui.runOnIdle{transfers.download(c.active!!,url,"video/mp4",null)}
        ui.waitUntil(10000){transfers.items.any{it.status==DownloadManager.STATUS_FAILED}}
        assertTrue(catalog.list().none{it.id !in before});assertTrue(ui.activity.getSharedPreferences("page-download-pending-v1",0).all.isEmpty())
    }
    @Test fun abandonedPendingOutputIsRemovedAndExplainedOnRecovery(){
        runBlocking{
            val storage=PageFileStorage(ui.activity);val partial=storage.create("interrupted.mp4","video/mp4","",4000)
            partial.output.write(byteArrayOf(1,2,3));partial.output.close();storage.recover()
            ui.activity.contentResolver.query(partial.uri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)?.use{assertFalse(it.moveToFirst())}
        }
        val row=catalog.list().first{it.id !in before};assertEquals(DownloadManager.STATUS_FAILED,row.status);assertTrue(row.detail.contains("中斷"))
    }
}
