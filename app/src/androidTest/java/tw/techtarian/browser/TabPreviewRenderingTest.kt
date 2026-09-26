package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TabPreviewRenderingTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val f get()=PreviewFixture(ui)
    @Before fun isolate(){assumeTrue(ui.activity.packageName.endsWith(".qa"));f.reset()}
    @After fun cleanup(){ui.runOnIdle{c.sheet=""}}
    private fun eval(tab:BrowserTab,code:String):String{
        val done=CountDownLatch(1);val result=AtomicReference("null")
        ui.runOnUiThread{tab.web.evaluateJavascript(code){result.set(it);done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    private fun frame(tab:BrowserTab){
        val done=CountDownLatch(1)
        ui.runOnUiThread{tab.web.postVisualStateCallback(19,object:WebView.VisualStateCallback(){override fun onComplete(id:Long){done.countDown()}})}
        assertTrue(done.await(10,TimeUnit.SECONDS));ui.waitForIdle()
    }
    private fun imageData():String{
        val image=Bitmap.createBitmap(320,960,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.rgb(20,70,220))
        val paint=Paint().apply{color=Color.WHITE;textSize=30f}
        repeat(6){i->canvas.drawRect(30f,30f+i*150,130f,100f+i*150,paint);canvas.drawText("PANEL ${i+1}",150f,80f+i*150,paint)}
        val bytes=ByteArrayOutputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};image.recycle()
        return "data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
    }
    private fun blueRatio(bitmap:Bitmap?):Double{
        if(bitmap==null)return 0.0
        var blue=0;var total=0
        for(y in 4 until bitmap.height-4 step 3)for(x in 4 until bitmap.width-4 step 3){
            val p=bitmap.getPixel(x,y);if(Color.blue(p)>160&&Color.red(p)<80&&Color.green(p)<120)blue++;total++
        }
        return blue.toDouble()/total.coerceAtLeast(1)
    }
    @Test fun scrolledImagePageCapturesTheVisiblePanelInsteadOfEmptyOffscreenTiles(){
        val tab=f.load("https://preview-render.invalid/long","Long image preview")
        val html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Long image preview</title></head><body style='margin:0;background:white'><div style='height:12000px'>PAGE TOP</div><img id='panel' style='width:100%;display:block' src='${imageData()}'></body></html>"
        ui.runOnUiThread{tab.web.loadDataWithBaseURL(tab.url,html,"text/html","UTF-8",null)}
        ui.waitUntil(15000){eval(tab,"document.getElementById('panel')?.complete===true")=="true"&&tab.pendingUrl.isEmpty()}
        eval(tab,"document.getElementById('panel').scrollIntoView();true");frame(tab)
        assertTrue(eval(tab,"scrollY").toDouble()>10_000)
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        f.screenshot("preview-long-image-scrolled")
        assertTrue("The thumbnail must show the actual blue image in the current viewport",blueRatio(tab.preview)>.45)
    }
    @Test fun aBlankReloadCannotReplaceTheSavedPreview(){
        val tab=f.load("https://preview-render.invalid/blank","Keep the good preview")
        // Title/load completion can precede the final painted frame. Establish a
        // rendered baseline before comparing the exact saved bytes after reload.
        frame(tab)
        ui.runOnIdle{c.checkpointTabs()};val key=tab.previewKey!!;val before=f.hash(key)
        ui.runOnUiThread{tab.web.loadDataWithBaseURL(tab.url,"<html><head><title>Blank loading frame</title></head><body style='background:white'></body></html>","text/html","UTF-8",null)}
        ui.waitUntil(15000){tab.title=="Blank loading frame"&&tab.pendingUrl.isEmpty()};frame(tab)
        ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
        assertEquals("A loading/empty frame must not destroy the durable thumbnail",before,f.hash(key))
    }
    @Test fun reopeningWithAnEmptyNetworkResponseKeepsThePreviouslySavedPixels(){
        val server=PreviewPageServer(imageData())
        try{
            ui.runOnIdle{c.navigate(server.url)};val original=c.active!!
            ui.waitUntil(15000){original.title=="Preview server"&&original.pendingUrl.isEmpty()};frame(original)
            ui.runOnIdle{c.checkpointTabs()};val key=original.previewKey!!;val before=f.hash(key)
            assertTrue(blueRatio(original.preview)>.45)
            server.blank=true
            ui.activityRule.scenario.recreate();ui.waitForIdle()
            ui.waitUntil(15000){c.active?.title=="Empty restored response"&&c.active?.pendingUrl==""}
            val restored=c.active!!;frame(restored)
            ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
            assertEquals(key,restored.previewKey)
            assertEquals(before,f.hash(key));assertTrue(blueRatio(restored.preview)>.45)
            f.screenshot("preview-restored-before-revisit")
        }finally{server.close()}
    }
    @Test fun aVisiblePageCanBeCapturedWhileAnotherResourceIsStillLoading(){
        val server=PreviewPageServer(imageData(),holdImage=true)
        try{
            ui.runOnIdle{c.navigate(server.url)};val tab=c.active!!
            ui.waitUntil(15000){tab.title=="Preview server"&&eval(tab,"document.getElementById('panel')?.complete===true")=="true"}
            frame(tab);assertTrue("The fixture must still be loading its stalled resource",tab.pendingUrl.isNotEmpty())
            ui.runOnIdle{c.openTabOverview();c.checkpointTabs()}
            assertTrue("Visible images must not wait for every ad/resource to finish",blueRatio(tab.preview)>.45)
        }finally{server.close()}
    }
    @Test fun aCommitBeforeTheFirstPositiveLayoutGetsACaptureWhenTheViewIsSized(){
        val tab=f.load("https://preview-render.invalid/layout","Layout readiness")
        val before=tab.previewGeneration
        ui.runOnIdle{
            val width=tab.web.width;val height=tab.web.height
            assertTrue(width>0&&height>0)
            tab.web.layout(0,0,0,0)
            tab.web.webViewClient.onPageStarted(tab.web,tab.url,null)
            tab.web.webViewClient.onPageCommitVisible(tab.web,tab.url)
            assertFalse(tab.previewReady)
            tab.web.layout(0,0,width,height)
        }
        ui.waitUntil(10000){tab.previewReady&&tab.previewGeneration>before}
        assertNotNull(tab.preview)
    }
}

private class PreviewPageServer(private val image:String,private val holdImage:Boolean=false):AutoCloseable {
    private val server=ServerSocket(0)
    private val release=CountDownLatch(1)
    @Volatile var blank=false
    val url="http://127.0.0.1:${server.localPort}/reader"
    private val worker=Thread{
        while(!server.isClosed)runCatching{val socket=server.accept()
            Thread{
                runCatching{socket.use{
                    socket.soTimeout=5000
                    val reader=socket.getInputStream().bufferedReader();val path=reader.readLine()?.split(' ')?.getOrNull(1)
                    while(!reader.readLine().isNullOrEmpty()){}
                    if(path=="/pending"){release.await(60,TimeUnit.SECONDS);return@use}
                    val title=if(blank)"Empty restored response"else"Preview server"
                    val body=if(blank)""else"<img id='panel' style='width:100%;display:block' src='$image'>"+if(holdImage)"<img width='1' height='1' src='/pending'>"else""
                    val bytes="<html><head><title>$title</title><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:white'>$body</body></html>".toByteArray()
                    socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
                }}
            }.apply{isDaemon=true;start()}
        }
    }.apply{isDaemon=true;start()}
    override fun close(){release.countDown();server.close();worker.join(1000)}
}
