package tw.techtarian.browser

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Existing regression fixtures are Chinese; production first-launch detection remains untouched. */
class BrowserTestRunner:AndroidJUnitRunner(){
    private var testLocale="zh-TW"
    override fun onCreate(arguments:Bundle){testLocale=arguments.getString("testLocale","zh-TW");super.onCreate(arguments)}
    override fun onStart(){
        // "system" leaves clean preferences untouched, exercising production first-launch detection.
        if(targetContext.packageName.endsWith(".qa")&&testLocale!="system")
            runOnMainSync{AppLanguages.select(targetContext,testLocale)}
        super.onStart()
    }
}
