package tw.techtarian.browser.smoketests;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import org.junit.Rule;
import org.junit.rules.TestName;
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
    @Rule public TestName testName=new TestName();
    private UiDevice device;
    private Fixture fixture;
    @Before public void start() throws Exception {
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        fixture=new Fixture();
        // Disposable QA package only. Each scenario starts with no account or private data.
        assertTrue(device.executeShellCommand("pm clear " + APP).contains("Success"));
        String method=testName.getMethodName();
        open(method.equals("scrolledImagePreviewSurvivesRestartAndBlankReload")?"reader":method.equals("imageDownloadPreviewCopyAndShareWorkInOptimizedRelease")||method.equals("appearanceFollowsAppChoiceAcrossSystemModes")?"image-actions":"one");
    }
    @After public void finish() throws Exception {
        if(device!=null){
            device.executeShellCommand("mkdir -p /data/local/tmp/r8-smoke");
            device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/"+testName.getMethodName()+".png");
            // The shell runner stops the app after this instrumentation process exits.
            // Loading its code registers a process dependency; force-stop here kills the test host too.
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
    private void launchPage(String page) throws Exception {
        device.executeShellCommand("am start -W -a android.intent.action.VIEW -d http://127.0.0.1:"+fixture.port()+"/"+page+" -n "+APP+"/tw.techtarian.browser.MainActivity");
        require(By.desc("瀏覽器選單"));
    }
    private void open(String page) throws Exception {
        launchPage(page);
        require(By.text("R8 測試元件"));
    }
    private UiObject2 require(BySelector selector) {
        UiObject2 node=device.wait(Until.findObject(selector),20000);
        assertNotNull("Missing UI: "+selector,node);
        return node;
    }
    private UiObject2 findText(String text) throws Exception {
        for(int i=0;i<7;i++){
            device.waitForIdle();
            UiObject2 node=device.wait(Until.findObject(By.text(text)),1000);
            if(node!=null)return node;
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,
                device.getDisplayWidth()/2,device.getDisplayHeight()/3,30);
            SystemClock.sleep(200);
        }
        fail("Missing text: "+text);
        return null;
    }
    private void click(String text) throws Exception {
        findText(text);
        // A text node can become visible during an unfinished scroll. Wait, re-query,
        // and tap its actual clickable row rather than stale text coordinates.
        device.waitForIdle();
        SystemClock.sleep(250);
        UiObject2 node=require(By.text(text));
        while(node!=null&&!node.isClickable())node=node.getParent();
        assertNotNull("No clickable target for: "+text,node);
        node.click();device.waitForIdle();
    }
    private boolean isInSelectedTab(UiObject2 node) {
        while(node!=null){
            if(node.isSelected())return true;
            node=node.getParent();
        }
        return false;
    }
    private void settings(String category) throws Exception {
        menu();click("設定");
        require(By.text("外觀"));require(By.text("瀏覽"));
        // Compose correctly removes ACTION_CLICK from the already selected tab.
        // Reopening Settings preserves its category; verify it rather than trying to
        // click a deliberately non-actionable selection for a second time.
        if(!isInSelectedTab(require(By.text(category))))click(category);
        if(category.equals("瀏覽"))require(By.desc("顯示首頁按鈕"));
        else if(category.equals("AI"))require(By.text("OpenRouter · 雲端模型"));
    }
    private void menu() {require(By.desc("瀏覽器選單")).click();require(By.desc("關閉選單"));}
    private void closeMenu() {require(By.desc("關閉選單")).click();require(By.desc("瀏覽器選單"));}

    @Test public void findInPageKeepsTheDocumentVisibleAndCountsMatches() throws Exception {
        menu();click("尋找頁面文字");
        assertFalse(device.hasObject(By.desc("關閉選單")));
        require(By.text("R8 測試元件")); // Same live document still visible, not a full-screen sheet.
        UiObject2 input=require(By.clazz("android.widget.EditText"));
        input.setText("R8");
        require(By.desc("第 1 筆，共 2 筆"));
        require(By.desc("下一筆符合文字")).click();
        require(By.desc("第 2 筆，共 2 筆"));
        require(By.desc("上一筆符合文字")).click();
        require(By.desc("第 1 筆，共 2 筆"));
        require(By.clazz("android.widget.EditText")).setText("no-such-search-marker");
        require(By.desc("找不到符合文字"));
        require(By.desc("關閉頁面搜尋")).click();
        assertTrue(device.wait(Until.gone(By.desc("關閉頁面搜尋")),5000));
        require(By.text("R8 測試元件"));
        require(By.desc("瀏覽器選單"));
    }

    @Test public void installedReleaseIsActuallyObfuscatedAndNotDebuggable() throws Exception {
        assertEquals(0,target().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        assertEquals(33,target().getPackageManager().getPackageInfo(APP,0).getLongVersionCode());
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
        Class<?> sampler=type(InstrumentationRegistry.getArguments().getString("samplerClass"));
        for(String getter:new String[]{"getTopK","getTopP","getTemperature","getSeed"})assertNotNull(sampler.getDeclaredMethod(getter));
        Class<?> thinking=type(InstrumentationRegistry.getArguments().getString("thinkingClass"));
        assertNotNull(thinking.getDeclaredMethod("getEnableThinking"));
        assertNotNull(thinking.getDeclaredMethod("getThinkingTokenBudget"));
    }
    @Test public void aiProviderFieldsStillRecomposeInOptimizedRelease() throws Exception {
        settings("AI");click("OpenRouter · 雲端模型");
        require(By.clazz("android.widget.EditText"));
        device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()/3,
            device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,25);
        click("Gemma 4 · 手機本機");
        require(By.text("Gemma 4 E2B"));
        assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")),5000));
    }
    @Test public void webViewJavascriptBridgeSurvivesOptimization() throws Exception {
        click("天眼");require(By.text("R8 測試元件")).click();
        require(By.text("選中一個元件"));require(By.text("移除此網站元件"));closeMenu();
    }
    private void sourceApp(String page) throws Exception {
        device.executeShellCommand("am start -W -n tw.techtarian.browser.smoketests/.BrowserLinkSourceActivity --es url http://127.0.0.1:"+fixture.port()+"/"+page);
        require(By.desc("external-link-source"));
    }
    private void launcherOpen() throws Exception {
        device.executeShellCommand("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "+APP+"/tw.techtarian.browser.MainActivity");
        require(By.desc("瀏覽器選單"));
    }
    @Test public void externalBackReturnsToCallerWithoutAddingHomeTabs() throws Exception {
        sourceApp("two");
        for(int i=0;i<3;i++){
            require(By.desc("open-browser-article")).click();require(By.text("R8 功能測試 two"));
            device.pressBack();require(By.desc("external-link-source"));
            assertEquals("Back must reveal the actual caller", "tw.techtarian.browser.smoketests",device.getCurrentPackageName());
        }
        launcherOpen();require(By.descStartsWith("分頁，")).click();require(By.text("一般 1"));require(By.text("R8 功能測試 one"));
        assertFalse(device.hasObject(By.text("R8 功能測試 two")));
    }
    @Test public void coldExternalBackAndProcessRestoreDoNotLeaveBlankTabs() throws Exception {
        assertTrue(device.executeShellCommand("pm clear "+APP).contains("Success"));
        sourceApp("two");require(By.desc("open-browser-article")).click();require(By.text("R8 功能測試 two"));
        // The saved source marker must survive a real process restart without replaying the intent.
        device.executeShellCommand("am force-stop "+APP);launcherOpen();require(By.text("R8 功能測試 two"));
        device.pressBack();require(By.desc("external-link-source"));
        for(int i=0;i<2;i++){
            require(By.desc("open-browser-article")).click();require(By.text("R8 功能測試 two"));device.pressBack();require(By.desc("external-link-source"));
        }
        launcherOpen();require(By.text("快速前往"));require(By.descStartsWith("分頁，")).click();require(By.text("一般 1"));
        assertFalse(device.hasObject(By.text("R8 功能測試 two")));
    }
    @Test public void regularRootBackKeepsItsPageAfterLeavingTheBrowser() throws Exception {
        require(By.desc("新增分頁")).click();require(By.text("快速前往"));
        UiObject2 address=require(By.clazz("android.widget.EditText"));address.click();device.waitForIdle();
        String url="http://127.0.0.1:"+fixture.port()+"/two";
        address=require(By.clazz("android.widget.EditText"));address.setText(url);require(By.text(url));SystemClock.sleep(250);device.pressEnter();
        require(By.text("R8 功能測試 two"));
        device.pressBack();assertTrue("Root Back must leave without visiting a homepage",device.wait(Until.gone(By.desc("瀏覽器選單")),10000));
        launcherOpen();require(By.descStartsWith("分頁，")).click();require(By.text("一般 2"));
        require(By.text("R8 功能測試 one"));require(By.text("R8 功能測試 two"));
    }
    @Test public void savedRuleEditorAndAiRevisionEntryWorkInOptimizedRelease() throws Exception {
        click("天眼");require(By.text("R8 測試元件")).click();click("修改這段代碼");
        UiObject2 html=require(By.clazz("android.widget.EditText"));
        assertTrue(device.wait(Until.hasObject(By.text("R8 測試元件")),10000));
        html.setText("<span>R8 original edit</span>");click("儲存並套用");
        require(By.text("R8 original edit"));menu();click("天眼設定");click("已儲存的網站修改");click("編輯 HTML");
        require(By.text("編輯網站規則"));click("請 AI 修改這項規則");
        require(By.text("讓原本的修改更合心意"));require(By.textContains("套用天眼前的 HTML"));
        require(By.desc("返回上一層")).click();require(By.text("編輯網站規則"));
        findText("HTML");device.waitForIdle();
        java.util.List<UiObject2> inputs=device.findObjects(By.clazz("android.widget.EditText"));
        assertFalse(inputs.isEmpty());html=inputs.get(inputs.size()-1);
        assertTrue("Editor must preserve the old rule",html.getText().contains("R8 original edit"));
        html.setText("<span>R8 revised edit</span>");click("儲存並套用");
        require(By.text("R8 revised edit"));assertFalse(device.hasObject(By.text("R8 original edit")));
    }
    @Test public void menuHistoryAndPrivateTabCollectionsStillOpen() throws Exception {
        menu();click("瀏覽記錄");require(By.text("搜尋瀏覽記錄"));
        require(By.text("R8 功能測試 one"));closeMenu();
        menu();click("下載");require(By.text("搜尋下載"));closeMenu();
        menu();click("Google 同步");findText("使用 Google 帳戶連結");closeMenu();
        menu();click("新增無痕分頁");require(By.text("無痕瀏覽"));
        require(By.descStartsWith("分頁，")).click();
        require(By.textStartsWith("一般 "));require(By.text("無痕 1"));
        assertFalse(device.hasObject(By.text("關閉所有分頁")));
        require(By.desc("分頁選單")).click();click("關閉所有分頁");require(By.text("關閉所有無痕分頁？"));
        java.util.List<UiObject2> confirms=device.findObjects(By.text("全部關閉"));
        assertFalse(confirms.isEmpty());confirms.get(confirms.size()-1).click();
    }
    @Test public void closeAllRegularTabsWorksInOptimizedRelease() throws Exception {
        // A second WebView can render before its virtual accessibility tree is refreshed.
        // This scenario tests the native overview: verify BOTH real page titles there.
        // The independent bridge scenario above still requires and clicks the DOM button.
        launchPage("two");
        require(By.descStartsWith("分頁，")).click();
        require(By.text("R8 功能測試 one"));require(By.text("R8 功能測試 two"));
        require(By.text("新增分頁"));
        assertFalse(device.hasObject(By.text("關閉所有分頁")));
        require(By.desc("分頁選單")).click();click("關閉所有分頁");require(By.text("關閉所有一般分頁？"));
        click("取消");require(By.text("R8 功能測試 one"));require(By.text("R8 功能測試 two"));
        assertFalse(device.hasObject(By.text("關閉所有分頁")));
        require(By.desc("分頁選單")).click();click("關閉所有分頁");click("全部關閉");
        require(By.desc("瀏覽器選單"));
        require(By.descStartsWith("分頁，")).click();
        require(By.text("一般 1"));
        assertFalse(device.hasObject(By.text("R8 功能測試 one")));
        assertFalse(device.hasObject(By.text("R8 功能測試 two")));
    }
    @Test public void deleteBrowsingDataWorksInOptimizedRelease() throws Exception {
        menu();click("刪除瀏覽資料");require(By.text("時間範圍"));
        click("過去 15 分鐘");click("不限時間");
        require(By.text("不限時間 · 全部清除，可能會登出網站"));
        click("Cookie、快取和網站資料");click("刪除資料");
        require(By.desc("瀏覽器選單"));
        require(By.descStartsWith("分頁，")).click();require(By.text("一般 1"));
        assertFalse(device.hasObject(By.text("R8 功能測試 one")));device.pressBack();require(By.desc("瀏覽器選單"));
        menu();click("瀏覽記錄");require(By.text("尚無瀏覽記錄"));
    }
    @Test public void externalDeepLinksWorkInOptimizedRelease() throws Exception {
        click("Open linked app");
        require(By.desc("deep-link-received:chengjing-test://open/r8?id=custom"));
        device.pressBack();require(By.desc("瀏覽器選單"));
        click("Open intent app");
        require(By.desc("deep-link-received:chengjing-test://open/r8?id=intent"));
        device.pressBack();require(By.desc("瀏覽器選單"));
        require(By.text("R8 功能測試 one"));
    }
    private double blueScreenRatio() {
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);int blue=0,total=0;
        for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4){
            int p=image.getPixel(x,y);if(Color.blue(p)>160&&Color.red(p)<80&&Color.green(p)<120)blue++;total++;
        }
        image.recycle();return (double)blue/Math.max(1,total);
    }
    private double blueOutside(android.graphics.Rect bounds) {
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);int blue=0,total=0;
        for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4)if(!bounds.contains(x,y)){
            int p=image.getPixel(x,y);if(Color.blue(p)>160&&Color.red(p)<80&&Color.green(p)<120)blue++;total++;
        }
        image.recycle();return (double)blue/Math.max(1,total);
    }
    @Test public void scrolledImagePreviewSurvivesRestartAndBlankReload() throws Exception {
        click("Show reading panels");device.waitForIdle();
        // Accessibility idle does not imply Chromium has presented the scrolled image frame.
        long paintDeadline=SystemClock.uptimeMillis()+10000;
        while(blueScreenRatio()<=.1&&SystemClock.uptimeMillis()<paintDeadline)SystemClock.sleep(150);
        assertTrue("The actual scrolled page must show the image fixture",blueScreenRatio()>.1);
        require(By.descStartsWith("分頁，")).click();require(By.text("R8 image reader"));device.waitForIdle();
        assertTrue("The overview must contain blue image pixels, not a blank thumbnail",blueScreenRatio()>.01);
        fixture.emptyReader=true;
        // This UI-only method never loads :app classes, so its host and HTTP server
        // survive the real browser-process stop (unlike the separate JNI test).
        device.executeShellCommand("am force-stop "+APP);
        device.executeShellCommand("am start -W -n "+APP+"/tw.techtarian.browser.MainActivity");
        require(By.descStartsWith("分頁，")).click();require(By.text("Restored empty reader"));
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500,10000);
        assertTrue("The saved image must remain visible after a real restart and blank response",blueScreenRatio()>.01);
    }
    @Test public void homepageOptionsAndDailyQuoteWorkInOptimizedRelease() throws Exception {
        assertFalse(device.hasObject(By.desc("首頁")));
        settings("瀏覽");
        assertFalse(device.hasObject(By.text("自訂網址")));
        require(By.desc("顯示首頁按鈕")).click();
        require(By.text("澄境首頁"));require(By.text("自訂網址"));
        closeMenu();
        require(By.desc("首頁")).click();require(By.text("快速前往"));
        String count=require(By.descStartsWith("分頁，")).getContentDescription();
        settings("瀏覽");click("自訂網址");
        require(By.clazz("android.widget.EditText")).setText("http://127.0.0.1:"+fixture.port()+"/two");
        click("儲存首頁");closeMenu();
        require(By.desc("首頁")).click();require(By.text("R8 功能測試 two"));
        assertEquals(count,require(By.descStartsWith("分頁，")).getContentDescription());
        settings("瀏覽");click("澄境首頁");closeMenu();
        require(By.desc("首頁")).click();require(By.text("今日一句"));
        settings("瀏覽");require(By.desc("顯示首頁按鈕")).click();
        assertFalse(device.hasObject(By.text("自訂網址")));closeMenu();
        assertFalse(device.hasObject(By.desc("首頁")));
    }
    @Test public void searchEngineChoiceAndCustomTemplateWorkInOptimizedRelease() throws Exception {
        settings("瀏覽");click("預設搜尋引擎");
        for(String label:new String[]{"Google","Bing","Yahoo","百度","Naver","Wiki","自訂"})findText(label);
        click("Wiki");require(By.text("中文維基百科"));
        require(By.desc("關閉選單")).click();require(By.desc("瀏覽器選單"));
        UiObject2 address=require(By.clazz("android.widget.EditText"));address.click();device.waitForIdle();
        address=require(By.clazz("android.widget.EditText"));address.clear();address.setText("r8 search terms");
        require(By.text("r8 search terms"));SystemClock.sleep(250);device.pressEnter();
        assertTrue("Address text must use the selected Wiki engine",device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("https://zh\\.wikipedia\\.org/w/index\\.php\\?search=r8(?:%20|\\+)search(?:%20|\\+)terms.*"))),10000));
        settings("瀏覽");click("預設搜尋引擎");click("自訂");
        UiObject2 template=require(By.clazz("android.widget.EditText"));template.setText("https://find.example/search?lang=zh-TW&q={query}");
        click("儲存並使用");require(By.desc("返回上一層")).click();
        require(By.textContains("自訂 · find.example"));click("預設搜尋引擎");require(By.text("find.example"));
        device.executeShellCommand("mkdir -p /data/local/tmp/r8-smoke");
        device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/search-engine-custom.png");
    }
    private void imageMenu() {
        UiObject2 image=device.wait(Until.findObject(By.desc("R8 image target")),5000);
        if(image==null)image=require(By.text("R8 image target"));
        image.longClick();
        require(By.text("下載圖片"));require(By.text("預覽圖片"));require(By.text("在新分頁開啟圖片"));
    }
    private void assertImageMenuTone(boolean dark) throws Exception {
        imageMenu();device.waitForIdle();
        UiObject2 row=require(By.text("下載圖片"));while(row.getParent()!=null&&!row.isClickable())row=row.getParent();
        android.graphics.Rect bounds=row.getVisibleBounds();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        int color=image.getPixel(bounds.left+6,bounds.centerY());image.recycle();
        int light=(Color.red(color)+Color.green(color)+Color.blue(color))/3;
        assertTrue("Wrong image-menu surface, dark="+dark+", color="+Integer.toHexString(color),dark?light<90:light>200);
        device.executeShellCommand("mkdir -p /data/local/tmp/r8-smoke");
        device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/appearance-"+(dark?"dark":"light")+".png");
        require(By.desc("關閉長按選單")).click();
    }
    @Test public void appearanceFollowsAppChoiceAcrossSystemModes() throws Exception {
        String tabs=require(By.descStartsWith("分頁，")).getContentDescription();
        try {
            device.executeShellCommand("cmd uimode night no");
            settings("外觀");click("深色");closeMenu();assertImageMenuTone(true);
            device.executeShellCommand("cmd uimode night yes");
            settings("外觀");click("淺色");closeMenu();assertImageMenuTone(false);
            settings("外觀");click("系統");closeMenu();assertImageMenuTone(true);
            device.executeShellCommand("cmd uimode night no");assertImageMenuTone(false);
            assertEquals("Theme changes must keep the current tabs",tabs,require(By.descStartsWith("分頁，")).getContentDescription());
            menu();require(By.desc("關閉選單"));launchPage("two");
            assertTrue("An external URL must dismiss the old panel",device.wait(Until.gone(By.desc("關閉選單")),5000));
        }finally{device.executeShellCommand("cmd uimode night no");}
    }
    private void chooseImageReceiver() throws Exception {
        for(int i=0;i<4;i++){
            device.waitForIdle();SystemClock.sleep(250);
            UiObject2 node=device.wait(Until.findObject(By.textStartsWith("QA 圖片")),1500);
            if(node!=null){while(node!=null&&!node.isClickable())node=node.getParent();assertNotNull(node);node.click();return;}
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*4/5,device.getDisplayWidth()/2,device.getDisplayHeight()/3,30);
        }
        fail("Image receiver missing from expanded system sharesheet");
    }
    @Test public void imageDownloadPreviewCopyAndShareWorkInOptimizedRelease() throws Exception {
        imageMenu();click("預覽圖片");
        UiObject2 image=require(By.desc("圖片，可雙指縮放與拖曳"));device.waitForIdle();
        device.executeShellCommand("mkdir -p /data/local/tmp/r8-smoke");
        device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/image-preview.png");
        double before=blueScreenRatio();android.graphics.Rect bounds=image.getVisibleBounds();
        device.click(bounds.centerX(),bounds.centerY());SystemClock.sleep(70);device.click(bounds.centerX(),bounds.centerY());device.waitForIdle();
        assertTrue("Double tap must visibly enlarge the preview",blueScreenRatio()>before+.05);
        assertTrue("Zoomed image must not cover preview controls",blueOutside(bounds)<.01);
        device.executeShellCommand("screencap -p /data/local/tmp/r8-smoke/image-preview-zoom.png");
        require(By.desc("關閉圖片預覽")).click();
        imageMenu();click("複製圖片");
        device.executeShellCommand("am start -W -n tw.techtarian.browser.smoketests/.ImageReceiverActivity");
        for(int i=0;i<10&&!device.hasObject(By.desc(fixture.expectedImage()));i++){click("Paste QA image");SystemClock.sleep(150);}
        require(By.desc(fixture.expectedImage()));
        device.pressBack();require(By.desc("瀏覽器選單"));
        imageMenu();click("分享圖片");chooseImageReceiver();require(By.desc(fixture.expectedImage()));
        device.pressBack();require(By.desc("瀏覽器選單"));
        imageMenu();click("下載圖片");
        menu();click("下載");require(By.text("搜尋下載"));require(By.text(java.util.regex.Pattern.compile(".*\\.png")));
    }
    @Test public void blobVideoAndDataAndHttpDownloadsOpenWithRealBytes() throws Exception {
        launchPage("page-downloads");require(By.text("Blob video"));click("Blob video");click("下載");
        menu();click("下載");require(By.text(fixture.videoName));
        require(By.desc("開啟 "+fixture.videoName)).click();chooseFileReceiver();require(By.desc(fixture.expectedDownload("video/mp4",fixture.video)));
        device.pressBack();closeMenu();
        click("Data PDF");click("下載");menu();click("下載");require(By.desc("開啟 "+fixture.dataName)).click();chooseFileReceiver();require(By.desc(fixture.expectedDownload("application/pdf",fixture.pdf)));
        device.pressBack();closeMenu();
        click("HTTP PDF");click("下載");menu();click("下載");
        UiObject2 open=null;
        for(int i=0;i<20&&open==null;i++){open=device.wait(Until.findObject(By.desc(java.util.regex.Pattern.compile("開啟 "+java.util.regex.Pattern.quote(fixture.httpName.substring(0,fixture.httpName.length()-4))+"-.*\\.pdf"))),1000);}
        assertNotNull("System HTTP download must complete",open);open.click();chooseFileReceiver();require(By.desc(fixture.expectedDownload("application/pdf",fixture.pdf)));
    }
    private void chooseFileReceiver() throws Exception {
        for(int i=0;i<5;i++){
            if(device.hasObject(By.descStartsWith("download-received:")))return;
            UiObject2 node=device.wait(Until.findObject(By.textStartsWith("QA 檔案")),1000);
            if(node!=null){while(node!=null&&!node.isClickable())node=node.getParent();assertNotNull(node);node.click();return;}
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*4/5,device.getDisplayWidth()/2,device.getDisplayHeight()/3,30);
        }
        fail("Download receiver missing from Android chooser");
    }
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile boolean running=true;
        volatile boolean emptyReader=false;
        private final byte[] image;
        final byte[] video;
        final byte[] pdf="%PDF-1.7\nQA fixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
        final String videoName="r8-video-"+System.nanoTime()+".mp4";
        final String dataName="r8-data-"+System.nanoTime()+".pdf";
        final String httpName="r8-http-"+System.nanoTime()+".pdf";
        Fixture() throws IOException {
            Bitmap bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);
            java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,png);bitmap.recycle();image=png.toByteArray();
            try(java.io.InputStream source=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("download-video.b64")){
                java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=source.read(buffer))!=-1)data.write(buffer,0,n);
                video=android.util.Base64.decode(data.toByteArray(),android.util.Base64.DEFAULT);
            }
            server=new ServerSocket(0);
            worker=new Thread(()->{
                while(running)try(Socket socket=server.accept()){
                    BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String first=input.readLine();String line;
                    while((line=input.readLine())!=null&&!line.isEmpty()){}
                    String page=first!=null&&first.contains("/two")?"two":"one";
                    String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>R8 功能測試 "+page+"</title></head><body style='margin:24px;font:18px sans-serif'><h1>R8 功能測試 "+page+"</h1><button id='r8-target' style='width:100%;padding:24px;margin:20px 0'>R8 測試元件</button><p>只使用本機合成內容，不接觸真實帳號。</p></body></html>";
                    html=html.replace("</body>","<p><a href='chengjing-test://open/r8?id=custom'>Open linked app</a></p><p><a href='intent://open/r8?id=intent#Intent;scheme=chengjing-test;package=tw.techtarian.browser.smoketests;end'>Open intent app</a></p></body>");
                    if(first!=null&&first.contains("/reader")){
                        String svg="<svg xmlns='http://www.w3.org/2000/svg' width='320' height='960'><rect width='320' height='960' fill='#1446dc'/><g fill='white'><rect x='30' y='30' width='90' height='80'/><rect x='30' y='230' width='90' height='80'/><rect x='30' y='430' width='90' height='80'/><rect x='30' y='630' width='90' height='80'/><rect x='30' y='830' width='90' height='80'/></g></svg>";
                        String src="data:image/svg+xml;base64,"+java.util.Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
                        String body=emptyReader?"":"<button id='r8-target'>R8 測試元件</button><p><a href='#panels'>Show reading panels</a></p><div style='height:12000px'></div><img id='panels' alt='Reading panels' src='"+src+"' style='display:block;width:100%'>";
                        html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+(emptyReader?"Restored empty reader":"R8 image reader")+"</title></head><body style='margin:0;background:white'>"+body+"</body></html>";
                    }
                    if(first!=null&&first.contains("/image-actions"))html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>R8 圖片操作</title></head><body style='margin:24px'><img role='img' aria-label='R8 image target' alt='R8 image target' tabindex='0' src='/image-without-extension' width='180' height='180'><p><button id='r8-target'>R8 測試元件</button></p></body></html>";
                    if(first!=null&&first.contains("/page-downloads")){
                        html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Page downloads</title><style>a{display:block;padding:24px;font:20px sans-serif}</style></head><body><a id='video'>Blob video</a><a href='data:application/pdf;base64,"+java.util.Base64.getEncoder().encodeToString(pdf)+"' download='"+dataName+"'>Data PDF</a><a href='/file-download'>HTTP PDF</a><script>const bytes=Uint8Array.from(atob('"+java.util.Base64.getEncoder().encodeToString(video)+"'),c=>c.charCodeAt(0));const a=document.getElementById('video');a.href=URL.createObjectURL(new Blob([bytes],{type:'video/mp4'}));a.download='"+videoName+"';</script></body></html>";
                    }
                    boolean imageRequest=first!=null&&first.contains("/image-without-extension");
                    boolean fileRequest=first!=null&&first.contains("/file-download");
                    byte[] bytes=fileRequest?pdf:imageRequest?image:html.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: "+(fileRequest?"application/pdf":imageRequest?"application/octet-stream":"text/html; charset=utf-8")+"\r\n"+(fileRequest?"Content-Disposition: attachment; filename=\""+httpName+"\"\r\n":"")+"Cache-Control: no-store\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
                }catch(IOException error){if(running)throw new RuntimeException(error);}
            },"r8-local-fixture");worker.setDaemon(true);worker.start();
        }
        int port(){return server.getLocalPort();}
        String expectedImage() throws Exception {
            StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(image))hash.append(String.format("%02x",b & 255));
            return "image-received:image/png:"+image.length+":"+hash;
        }
        String expectedDownload(String mime,byte[] data) throws Exception {
            StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format("%02x",b&255));
            return "download-received:"+mime+":"+data.length+":"+hash;
        }
        @Override public void close() throws Exception {running=false;server.close();worker.join(1000);}
    }
}
