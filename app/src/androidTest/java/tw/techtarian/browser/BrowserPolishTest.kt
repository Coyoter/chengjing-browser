package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class BrowserPolishTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val c get()=ui.activity.controller
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun eval(script:String):String{val latch=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;latch.countDown()}};check(latch.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)};assertTrue("Timed out",check())}
    private fun practice(){main{c.newTab("https://practice.chengjing.invalid/")};until{eval("document.readyState")=="\"complete\""&&eval("typeof window.__chengjingEye")=="\"object\""}}
    private fun tap(selector:String){
        val p=JSONObject(eval("(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"))
        val x=(p.getDouble("x")*p.getDouble("dpr")).toFloat();val y=(p.getDouble("y")*p.getDouble("dpr")).toFloat()
        main{val time=SystemClock.uptimeMillis();c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0));c.active!!.web.dispatchTouchEvent(MotionEvent.obtain(time,time+70,MotionEvent.ACTION_UP,x,y,0))}
    }
    private fun save(name:String,bitmap:Bitmap){
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChengJing-0.1.3-QA")}
        val uri=ui.activity.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        ui.activity.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun shot(name:String){ui.waitForIdle();Thread.sleep(500);save(name,InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())}
    @Test fun directPickerNeverDispatchesPageInputAndBlocksNavigations(){
        practice()
        eval("""document.body.innerHTML=`<style>body{margin:0;font:16px sans-serif} .item{display:block;margin:12px;padding:18px;background:#e4eee8;height:72px;box-sizing:border-box} iframe{width:90%;height:90px} .space{height:2000px}</style>
          <a id="link" class="item" href="https://example.com/redirect" target="_blank">連結元件</a>
          <button id="button" class="item" onclick="window.pageClicks++">按鈕</button>
          <form id="form" action="https://practice.chengjing.invalid/post" method="post"><button id="submit" class="item">提交按鈕</button></form>
          <iframe id="embedded" srcdoc="<a href='https://example.com/frame' target='_top'>內嵌連結</a>"></iframe><div id="dynamic-slot"></div><div class="space"></div>`;
          window.pageEvents=0;window.pageClicks=0;window.handlers=[];
          for(const type of ['touchstart','touchend','pointerdown','pointerup','mousedown','mouseup','click']){
            const handler=()=>{window.pageEvents++;window.open('https://example.com/popup');};
            window.handlers.push([type,handler]);window.addEventListener(type,handler,true);
          }""")
        val count=c.tabs.size;val url=c.active!!.url
        ui.onNodeWithText("天眼",useUnmergedTree=true).performClick()
        until{c.active!!.web.selecting&&eval("window.__chengjingEye.status().enabled")=="true"}
        assertEquals("",c.sheet)
        ui.onNodeWithText("開啟天眼・選取元件").assertDoesNotExist()
        for(selector in listOf("#link","#button","#submit","#embedded")){
            android.util.Log.i("CJPolish","Selecting $selector")
            main{c.selection=null;c.sheet=""};ui.waitForIdle();tap(selector);until{c.selection!=null}
            assertEquals(selector,c.selection!!.selector)
            assertEquals("0",eval("window.pageEvents"));assertEquals(count,c.tabs.size);assertEquals(url,c.active!!.url)
        }
        main{c.sheet=""};ui.waitForIdle();shot("01-direct-picking")
        // Native gates must also block background requests while the picker is active.
        eval("window.open('https://example.com/background');location.href='https://example.com/background-redirect'")
        Thread.sleep(700);assertEquals(count,c.tabs.size);assertEquals(url,c.active!!.url);assertTrue(c.eye)
        eval("document.querySelector('#form').submit()")
        Thread.sleep(800);assertEquals(url,c.active!!.url);assertEquals(url,org.json.JSONTokener(eval("location.href")).nextValue());assertEquals("",c.active!!.error);assertEquals("true",eval("!!document.querySelector('#link')"));assertTrue(c.eye)
        main{
            val w=c.active!!.web;val t=SystemClock.uptimeMillis();val x=w.width*.8f
            w.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,w.height*.8f,0))
            for(i in 1..8)w.dispatchTouchEvent(MotionEvent.obtain(t,t+i*45L,MotionEvent.ACTION_MOVE,x,w.height*(.8f-i*.06f),0))
            w.dispatchTouchEvent(MotionEvent.obtain(t,t+410,MotionEvent.ACTION_UP,x,w.height*.32f,0))
        }
        until{eval("window.scrollY").toDouble()>100}
        assertEquals("0",eval("window.pageEvents"))
        eval("for(const [type,handler] of window.handlers)window.removeEventListener(type,handler,true)")
        main{c.stopEye();c.sheet="";c.active!!.web.scrollTo(0,0)};ui.waitForIdle();tap("#button")
        until{eval("window.pageClicks")=="1"}
        assertEquals(url,c.active!!.url)
    }
    private fun glyphCentered(label:String){
        val bitmap=ui.onNodeWithTag("tab-count-badge",useUnmergedTree=true).captureToImage().asAndroidBitmap()
        save(label,bitmap)
        val margin=(bitmap.width*0.20).toInt();val background=bitmap.getPixel(bitmap.width/2,margin)
        var left=bitmap.width;var top=bitmap.height;var right=-1;var bottom=-1;var mass=0.0;var moment=0.0
        for(y in margin until bitmap.height-margin)for(x in margin until bitmap.width-margin){
            val p=bitmap.getPixel(x,y)
            val difference=abs(android.graphics.Color.red(p)-android.graphics.Color.red(background))+abs(android.graphics.Color.green(p)-android.graphics.Color.green(background))+abs(android.graphics.Color.blue(p)-android.graphics.Color.blue(background))
            if(difference>200){left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)}
            if(difference>30){mass+=difference;moment+=(x+.5)*difference}
        }
        assertTrue("Numeral should be visible",right>=left)
        if(label.contains("tab-one"))assertEquals("Optical center for one",bitmap.width/2f,(moment/mass).toFloat(),1f)
        else assertEquals("Horizontal ink center",bitmap.width/2f,(left+right+1)/2f,1f)
        assertEquals("Vertical ink center",bitmap.height/2f,(top+bottom+1)/2f,1f)
    }
    @Test fun compactSymmetricChromeAndBrand(){
        main{c.newTab();for(tab in c.tabs.toList().dropLast(1))c.closeTab(tab.id);c.sheet=""}
        val density=ui.activity.resources.displayMetrics.density
        ui.waitForIdle()
        val bar=ui.onNodeWithTag("browser-topbar",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val capsule=ui.onNodeWithTag("address-capsule",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val badge=ui.onNodeWithTag("tab-count-badge",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(44*density,capsule.height,1f)
        assertEquals(capsule.top-bar.top,bar.bottom-capsule.bottom,1f)
        assertEquals(capsule.center.y,badge.center.y,1f)
        glyphCentered("02-tab-one")
        shot("03-home-light-compact")
        ui.onNodeWithTag("address-input").performTextInput("example.com")
        ui.onNodeWithTag("address-input").performImeAction()
        until{c.active!!.url.startsWith("https://example.com")}
        until{eval("document.readyState")=="\"complete\""}
        shot("04-website-compact")
        main{repeat(11){c.newTab()}}
        glyphCentered("05-tab-twelve")
        main{c.sheet="settings"};ui.onNodeWithText("深色",useUnmergedTree=true).performClick();main{c.sheet=""}
        glyphCentered("06-tab-twelve-dark");shot("07-home-dark-compact")
        main{for(tab in c.tabs.toList().dropLast(1))c.closeTab(tab.id)}
        glyphCentered("09-tab-one-dark");shot("10-optical-one-dark")
        val icon=ui.activity.packageManager.getApplicationIcon(ui.activity.packageName)
        val bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,432,432);icon.draw(Canvas(bitmap));save("08-installed-adaptive-icon",bitmap)
    }
}
