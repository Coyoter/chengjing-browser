package tw.techtarian.browser.smoketests;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Black-box host: the minified app was built WITHOUT instrumentation/test keep rules. */
@RunWith(AndroidJUnit4.class)
public class OptimizedReleaseTest {
    private static final String APP = "tw.techtarian.browser.qa";
    private UiDevice device;
    private Fixture fixture;
    @Before public void start() throws Exception {
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        fixture=new Fixture();
        // Disposable QA package only. Each scenario starts with no account or private data.
        assertTrue(device.executeShellCommand("pm clear " + APP).contains("Success"));
        open("one");
    }
    @After public void finish() throws Exception {
        if(device!=null){
            device.executeShellCommand("mkdir -p /data/local/tmp/r8-smoke");
            device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/last-frame.png");
            device.executeShellCommand("am force-stop " + APP);
        }
        if(fixture!=null)fixture.close();
    }
    private Context target() throws Exception {
        return InstrumentationRegistry.getInstrumentation().getContext()
            .createPackageContext(APP,Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
    }
    private Class<?> type(String name) throws Exception {
        return Class.forName(name,true,target().getClassLoader());
    }
    private void open(String page) throws Exception {
        device.executeShellCommand("am start -W -a android.intent.action.VIEW -d http://127.0.0.1:"+fixture.port()+"/"+page+" -n "+APP+"/tw.techtarian.browser.MainActivity");
        require(By.desc("瀏覽器選單"));
        require(By.text("R8 測試元件"));
    }
    private UiObject2 require(BySelector selector) {
        UiObject2 node=device.wait(Until.findObject(selector),20000);
        assertNotNull("Missing UI: "+selector,node);
        return node;
    }
    private void click(String text) throws Exception {
        for(int i=0;i<7;i++){
            UiObject2 node=device.wait(Until.findObject(By.text(text)),1000);
            if(node!=null){node.click();device.waitForIdle();return;}
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,
                device.getDisplayWidth()/2,device.getDisplayHeight()/3,30);
            SystemClock.sleep(200);
        }
        fail("Missing text: "+text);
    }
    private void menu() {require(By.desc("瀏覽器選單")).click();require(By.desc("關閉選單"));}
    private void closeMenu() {require(By.desc("關閉選單")).click();require(By.desc("瀏覽器選單"));}

    @Test public void installedReleaseIsActuallyObfuscatedAndNotDebuggable() throws Exception {
        assertEquals(0,target().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        assertEquals(23,target().getPackageManager().getPackageInfo(APP,0).getLongVersionCode());
        try {type("tw.techtarian.browser.BrowserStore");fail("Unobfuscated application class still present");}
        catch(ClassNotFoundException expected) { }
    }
    @Test public void nativeJniCanLoadCallAndThrowItsTypedError() throws Exception {
        Class<?> jni=type("com.google.ai.edge.litertlm.LiteRtLmJni");
        Object receiver=jni.getDeclaredField("INSTANCE").get(null);
        Method log=jni.getDeclaredMethod("nativeSetMinLogSeverity",int.class);
        log.setAccessible(true);log.invoke(receiver,3);
        Method create=null;
        for(Method method:jni.getDeclaredMethods())if(method.getName().equals("nativeCreateEngine"))create=method;
        assertNotNull(create);
        Class<?>[] params=create.getParameterTypes();Object[] args=new Object[params.length];
        for(int i=0;i<args.length;i++){
            if(params[i]==String.class)args[i]=i==0?"/definitely-missing-r8-test-model.litertlm":"";
            else if(params[i]==int.class)args[i]=0;
            else if(params[i]==boolean.class||params[i]==Boolean.class)args[i]=false;
            else fail("Unexpected native signature: "+params[i]);
        }
        try {create.setAccessible(true);create.invoke(receiver,args);fail("Missing model must report a native error");}
        catch(InvocationTargetException error) {
            assertEquals("com.google.ai.edge.litertlm.LiteRtLmJniException",error.getCause().getClass().getName());
            assertTrue(error.getCause().getMessage().contains("not found"));
        }
        Class<?> sampler=type("com.google.ai.edge.litertlm.SamplerConfig");
        for(String getter:new String[]{"getTopK","getTopP","getTemperature","getSeed"})assertNotNull(sampler.getDeclaredMethod(getter));
        Class<?> thinking=type("com.google.ai.edge.litertlm.ThinkingConfig");
        assertNotNull(thinking.getDeclaredMethod("getEnableThinking"));
        assertNotNull(thinking.getDeclaredMethod("getThinkingTokenBudget"));
    }
    @Test public void aiProviderFieldsStillRecomposeInOptimizedRelease() throws Exception {
        menu();click("設定");click("AI");click("OpenRouter · 雲端模型");
        require(By.textContains("API Key"));
        device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()/3,
            device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,25);
        click("Gemma 4 · 手機本機");
        assertTrue(device.wait(Until.gone(By.textContains("API Key")),5000));
    }
    @Test public void webViewJavascriptBridgeSurvivesOptimization() throws Exception {
        click("天眼");require(By.text("R8 測試元件")).click();
        require(By.text("選中一個元件"));require(By.text("移除此網站元件"));closeMenu();
    }
    @Test public void menuHistoryAndPrivateTabCollectionsStillOpen() throws Exception {
        menu();click("歷史記錄");require(By.text("搜尋歷史記錄"));
        require(By.text("R8 功能測試 one"));closeMenu();
        menu();click("下載");require(By.text("搜尋下載"));closeMenu();
        menu();click("Google 同步");require(By.text("使用 Google 帳戶連結"));closeMenu();
        menu();click("新增無痕分頁");require(By.text("無痕瀏覽"));
        require(By.descStartsWith("分頁，")).click();
        require(By.textStartsWith("一般 "));require(By.text("無痕 1"));
        click("全部關閉");require(By.text("關閉全部無痕分頁？"));
        java.util.List<UiObject2> confirms=device.findObjects(By.text("全部關閉"));
        assertFalse(confirms.isEmpty());confirms.get(confirms.size()-1).click();
    }
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile boolean running=true;
        Fixture() throws IOException {
            server=new ServerSocket(0);
            worker=new Thread(()->{
                while(running)try(Socket socket=server.accept()){
                    BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String first=input.readLine();String line;
                    while((line=input.readLine())!=null&&!line.isEmpty()){}
                    String page=first!=null&&first.contains("/two")?"two":"one";
                    String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>R8 功能測試 "+page+"</title></head><body style='margin:24px;font:18px sans-serif'><h1>R8 功能測試 "+page+"</h1><button id='r8-target' style='width:100%;padding:24px;margin:20px 0'>R8 測試元件</button><p>只使用本機合成內容，不接觸真實帳號。</p></body></html>";
                    byte[] bytes=html.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
                }catch(IOException error){if(running)throw new RuntimeException(error);}
            },"r8-local-fixture");worker.setDaemon(true);worker.start();
        }
        int port(){return server.getLocalPort();}
        @Override public void close() throws Exception {running=false;server.close();worker.join(1000);}
    }
}
