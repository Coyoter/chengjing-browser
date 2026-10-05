package tw.techtarian.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object GoogleLoginPolicy {
    fun isEntry(raw:String):Boolean {
        val url=raw.toHttpUrlOrNull()?:return false
        if(url.scheme!="https"||url.host!="accounts.google.com"||url.port!=443||url.username.isNotEmpty()||url.password.isNotEmpty())return false
        return url.encodedPath=="/ServiceLogin"||url.encodedPath.startsWith("/signin/")||
            (listOf("/o/oauth2/auth","/o/oauth2/v2/auth","/oauth2/v2/auth").any{url.encodedPath==it||url.encodedPath.startsWith("$it/")}&&url.queryParameter("client_id").orEmpty().isNotBlank())
    }
    fun source(raw:String):String?=raw.toHttpUrlOrNull()?.takeIf{it.scheme=="https"&&it.host!="accounts.google.com"&&it.username.isEmpty()&&it.password.isEmpty()}?.toString()
}

/** Website authentication remains entirely in a separate browser's own session. */
internal class GoogleBrowserLogin(private val activity:MainActivity){
    var enabled:Boolean
        get()=activity.getSharedPreferences("browser-v1",0).getBoolean("google-browser-login",true)
        set(value){activity.getSharedPreferences("browser-v1",0).edit().putBoolean("google-browser-login",value).apply()}
    val hasGms get()=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)==ConnectionResult.SUCCESS
    @Suppress("DEPRECATION")
    fun provider():String?=runCatching{
        val pm=activity.packageManager
        val service=Intent("android.support.customtabs.action.CustomTabsService")
        val candidates=pm.queryIntentServices(service,0).map{it.serviceInfo.packageName}.toSet()
        val default=pm.resolveActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://example.invalid/")),PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        when{
            default!=null&&default!=activity.packageName&&default in candidates->default
            "com.android.chrome" in candidates->"com.android.chrome"
            else->null
        }
    }.getOrNull()
    fun open(page:String):Boolean {
        val url=page.toHttpUrlOrNull()?.takeIf{it.scheme=="https"&&it.username.isEmpty()&&it.password.isEmpty()}?:return false
        val pkg=provider()?:return false
        return try{
            val tab=CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
            tab.intent.setPackage(pkg);tab.launchUrl(activity,Uri.parse(url.toString()));true
        }catch(_:Exception){false}
    }
    fun offer(tab:BrowserTab,requested:String):Boolean {
        val c=activity.controller
        if(!enabled||tab.incognito||tab.web.selecting||tab.id!=c.activeId||!GoogleLoginPolicy.isEntry(requested)||!hasGms||provider()==null)return false
        val source=GoogleLoginPolicy.source(tab.url)?:return false
        val generation=tab.navigationGeneration
        c.prompts.confirm(bt(R.string.system_google_title),bt(R.string.system_google_prompt),bt(R.string.system_google_open),owner=tab.id,
            valid={tab in c.tabs&&tab.id==c.activeId&&tab.navigationGeneration==generation&&tab.url==source},
            cancelLabel=bt(R.string.system_google_stay)){if(!open(source))c.notice=bt(R.string.system_settings_unavailable)}
        return true
    }
}
