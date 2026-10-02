package tw.techtarian.browser.smoketests;

import android.os.Build;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Independent process verifies persisted language before Android has per-app language settings. */
public class LegacyLanguageTest {
    private static final String APP="tw.techtarian.browser.qa";
    private UiDevice device;
    @Before public void setup() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=28&&Build.VERSION.SDK_INT<33);
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertTrue("QA package must be installed",device.executeShellCommand("pm path "+APP).contains("package:"));
        assertTrue(device.executeShellCommand("pm clear "+APP).contains("Success"));
        launch("Browser menu");
    }
    @After public void cleanup() throws Exception {
        if(device!=null)device.executeShellCommand("am force-stop "+APP);
    }
    private UiObject2 require(BySelector selector){
        UiObject2 node=device.wait(Until.findObject(selector),10000);
        assertNotNull("Missing UI: "+selector,node);return node;
    }
    private void click(String text) throws Exception {
        for(int i=0;i<12;i++){
            UiObject2 node=device.wait(Until.findObject(By.text(text)),600);
            if(node!=null){node.click();device.waitForIdle();return;}
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,
                device.getDisplayWidth()/2,device.getDisplayHeight()/3,30);
            SystemClock.sleep(200);
        }
        fail("Missing text: "+text);
    }
    private void launch(String description) throws Exception {
        device.executeShellCommand("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "+APP+"/tw.techtarian.browser.MainActivity");
        require(By.desc(description));
    }
    private void settings(String description,String title) throws Exception {
        require(By.desc(description)).click();click(title);
    }
    @Test public void japaneseAndUrduPersistAcrossRealProcessRestart() throws Exception {
        settings("Browser menu","Settings");click("Follow device language");click("日本語");
        require(By.text("設定"));require(By.text("日本語"));
        device.executeShellCommand("am force-stop "+APP);launch("ブラウザメニュー");
        settings("ブラウザメニュー","設定");require(By.text("日本語"));
        click("日本語");click("اردو");require(By.text("سیٹنگز"));
        device.executeShellCommand("am force-stop "+APP);launch("براؤزر مینو");
        settings("براؤزر مینو","سیٹنگز");require(By.text("اردو"));
    }
}
