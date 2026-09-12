package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExceptionRecoveryTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private val server=ServerSocket(0)
    private val workers=Executors.newCachedThreadPool()
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(code:String,tab:BrowserTab=c.active!!):String{
        val latch=CountDownLatch(1);var result=""
        main{tab.web.evaluateJavascript(code){result=it;latch.countDown()}}
        check(latch.await(8,TimeUnit.SECONDS)){"Renderer did not answer"};return result
    }
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue("Page did not recover",check())}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(300)
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.7-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun ready(){until{eval("document.readyState")=="\"complete\""&&c.active!!.progress==100};ui.waitForIdle()}
    private fun url(host:String="127.0.0.1",path:String="/")="http://$host:${server.localPort}$path"
    @Before fun start(){
        Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        workers.execute{while(!server.isClosed){val socket=runCatching{server.accept()}.getOrNull()?:break;workers.execute{
            socket.use{runCatching{
                val reader=it.getInputStream().bufferedReader();val path=reader.readLine().split(' ')[1]
                while(!reader.readLine().isNullOrEmpty()){}
                val body=if(path.startsWith("/slow")){Thread.sleep(700);""}else """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>切換測試</title><style>body{padding:24px;height:2500px}a,button{display:block;margin:24px}</style><h1>網頁正常</h1><div id="hidden-target">可移除元件</div><a id="next" href="/next">下一頁</a><button id="clickable" onclick="this.textContent='操作成功'">點擊測試</button><script>
                    window.reconfigurations=0;const original=window.__chengjingEye.configure;window.__chengjingEye.configure=function(v){window.reconfigurations++;return original(v)};
                    window.addEventListener('pagehide',()=>sessionStorage.setItem('oldDocumentConfigurations',String(window.reconfigurations)));
                    setInterval(()=>document.body.dataset.tick=Date.now(),50);
                    </script><img src="/slow">"""
                val bytes=body.toByteArray();it.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray()+bytes)
            }}
        }}}
    }
    @After fun stop(){server.close();workers.shutdownNow()}
    @Test fun togglesLeaveOtherTabsAloneAndBrowsingRemainsUsable(){
        lateinit var background:BrowserTab
        main{background=c.newTab(url("localhost"))!!};ready()
        lateinit var related:BrowserTab
        main{related=c.newTab(url(path="/related"))!!};ready()
        main{c.newTab(url())};ready()
        val tab=c.active!!
        main{c.saveSite(c.site.copy(rules=listOf(ElementRule("#hidden-target")),guard=true))}
        until{eval("getComputedStyle(document.querySelector('#hidden-target')).display")=="\"none\""}
        eval("window.reconfigurations=0");eval("window.reconfigurations=0",background)
        val backgroundDocument=eval("performance.timeOrigin",background)
        repeat(6){index->
            val before=eval("performance.timeOrigin")
            ui.onNodeWithContentDescription("瀏覽器選單").performClick()
            ui.onNodeWithText(if(index%2==0)"暫時顯示原始網站"else"恢復套用天眼規則",substring=false).performScrollTo().performClick()
            until{eval("performance.timeOrigin")!=before};ready()
            until{related.progress==100 && eval("document.readyState",related)=="\"complete\""}
            assertEquals(index%2==1,eval("getComputedStyle(document.querySelector('#hidden-target')).display",related)=="\"none\"")
            assertEquals(index%2==0,c.isException)
            assertEquals(index%2==1,eval("getComputedStyle(document.querySelector('#hidden-target')).display")=="\"none\"")
            assertEquals("Old document should not be rewritten before reload","\"0\"",eval("sessionStorage.getItem('oldDocumentConfigurations')"))
        }
        assertEquals(backgroundDocument,eval("performance.timeOrigin",background))
        assertEquals("Unrelated document must stay untouched","0",eval("window.reconfigurations",background))
        main{c.beginEye();c.exception();c.exception()};ready()
        assertFalse(c.eye);assertFalse(tab.web.selecting);assertTrue(tab.refreshContainer.isEnabled)
        // A native link tap must still reach the page after leaving exception mode.
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("下一頁")),5000))
        device.findObject(androidx.test.uiautomator.By.text("下一頁")).click()
        until{c.active!!.url.endsWith("/next")};ready()
        ui.onNodeWithTag("address-input").performTouchInput{click()}
        ui.onNodeWithTag("address-input").performTextInput(url(path="/address"))
        ui.onNodeWithTag("address-input").performImeAction()
        until{c.active!!.url.endsWith("/address")};ready()
        main{c.newTab(url(path="/new-tab"))};ready()
        assertEquals("\"網頁正常\"",eval("document.querySelector('h1').textContent"))
        main{c.navigate("https://example.com/")};until{c.active!!.url.startsWith("https://example.com")};ready()
        assertEquals("\"Example Domain\"",eval("document.querySelector('h1').textContent"))
        ui.onNodeWithTag("address-input").performTouchInput{click()}
        ui.onNodeWithTag("address-input").performTextInput("澄境瀏覽器")
        ui.onNodeWithTag("address-input").performImeAction()
        until{c.active!!.url.contains("google.com/search")};ready()
        assertTrue(eval("document.body.innerText").contains("澄境"));shot("01-search-after-rule-toggles")
    }
    @Test fun toolbarSavesAndUpdatesFavoritesAndImportOnlyLivesInsideBookmarks(){
        main{c.newTab(url())};ready()
        val before=c.favorites.all().size
        ui.onNodeWithContentDescription("快速收藏").performClick()
        until{c.active!!.favoriteId!=null}
        assertEquals(before+1,c.favorites.all().size)
        val id=c.active!!.favoriteId!!
        eval("scrollTo(0,400)");until{eval("scrollY").toDouble()>0}
        ui.onNodeWithContentDescription("更新收藏進度").performClick()
        until{c.favorites.get(id)!!.scrollY>0}
        assertEquals(before+1,c.favorites.all().size)
        assertFalse(c.isException);shot("02-quick-favorite")
        ui.onNodeWithContentDescription("切換網站例外").assertDoesNotExist()
        ui.onNodeWithContentDescription("瀏覽器選單").performClick()
        ui.onNodeWithText("匯入 Chrome 書籤",substring=false).assertDoesNotExist()
        ui.onNodeWithText("書籤",substring=false).performScrollTo().performClick()
        ui.onNodeWithContentDescription("匯入 Chrome 書籤").assertIsDisplayed();shot("03-bookmark-import-location")
    }
}
