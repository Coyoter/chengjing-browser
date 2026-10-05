package tw.techtarian.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.autofill.AutofillManager
import android.webkit.WebView

/** Credentials stay in the selected Android provider; WebView supplies real form/domain metadata. */
internal object SystemAutofill {
    fun configure(web:WebView,private:Boolean){
        web.importantForAutofill=if(private)View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_AUTOFILL_YES
    }
    fun cancel(context:Context){runCatching{context.getSystemService(AutofillManager::class.java)?.cancel()}}
    fun provider(context:Context):String?=runCatching{
        val component=ComponentName.unflattenFromString(Settings.Secure.getString(context.contentResolver,"autofill_service").orEmpty())?:return null
        @Suppress("DEPRECATION")
        context.packageManager.getServiceInfo(component,0).loadLabel(context.packageManager).toString()
    }.getOrNull()
    fun settingsIntent(context:Context)=Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,Uri.parse("package:${context.packageName}"))
    fun openSettings(context:Context):Boolean=try{context.startActivity(settingsIntent(context));true}catch(_:Exception){
        try{context.startActivity(Intent(Settings.ACTION_SETTINGS));true}catch(_:Exception){false}
    }
}
