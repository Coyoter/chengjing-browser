package tw.techtarian.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class ExternalLink(val intent:Intent,val fallback:String?,val webLink:Boolean)

internal object ExternalLinks {
    private val browserPackages=setOf("tw.techtarian.browser","tw.techtarian.browser.qa")
    fun parse(raw:String):ExternalLink?=runCatching {
        if(!ExternalLinkPolicy.validInput(raw))return null
        val uri=Uri.parse(raw)
        val encodedIntent=uri.scheme.equals("intent",true)
        val parsed=if(encodedIntent)Intent.parseUri(raw,Intent.URI_INTENT_SCHEME)else null
        val destination=if(encodedIntent)parsed?.data else uri
        val fallback=parsed?.getStringExtra("browser_fallback_url")?.takeIf{ExternalLinkPolicy.validInput(it)}
            ?.toHttpUrlOrNull()?.toString()
        val target=destination?.takeIf{ExternalLinkPolicy.permittedScheme(it.scheme)}?:return null
        if(ExternalLinkPolicy.isWeb(target.scheme)&&target.toString().toHttpUrlOrNull()==null)return null
        // Copy only the URL and optional package. Never forward website-supplied components,
        // selectors, actions, grants, flags, ClipData, or nested/privileged Intent extras.
        val intent=Intent(Intent.ACTION_VIEW,target).addCategory(Intent.CATEGORY_BROWSABLE)
        parsed?.`package`?.let{pkg->
            if(!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(pkg)||pkg.length>255)return null
            intent.setPackage(pkg)
        }
        ExternalLink(intent,fallback?:if(ExternalLinkPolicy.isWeb(target.scheme))target.toString()else null,!encodedIntent&&ExternalLinkPolicy.isWeb(target.scheme))
    }.getOrNull()

    /** Respect Android's verified/default app choice; ordinary links stay in this browser. */
    @Suppress("DEPRECATION")
    fun hasDefaultWebApp(context:Context,intent:Intent):Boolean=runCatching {
        val pm=context.packageManager
        val resolved=pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY)?:return false
        val info=resolved.activityInfo?:return false
        if(!info.exported||info.packageName in browserPackages)return false
        val generic=Intent(Intent.ACTION_VIEW,Uri.parse("${intent.data?.scheme}://")).addCategory(Intent.CATEGORY_BROWSABLE)
        if(pm.queryIntentActivities(generic,PackageManager.MATCH_DEFAULT_ONLY).any{it.activityInfo?.packageName==info.packageName&&it.activityInfo?.name==info.name})return false
        // The system resolver is not a target app. Android 9/10 also need this check.
        pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY).any{
            it.activityInfo?.packageName==info.packageName&&it.activityInfo?.name==info.name
        }
    }.getOrDefault(false)

    fun launch(context:Context,link:ExternalLink):Boolean {
        if(link.intent.`package` in browserPackages)return false
        val intent=Intent(link.intent)
        if(link.webLink){
            if(!hasDefaultWebApp(context,intent))return false
            if(Build.VERSION.SDK_INT>=30)intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT)
        }
        if(context !is Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try{context.startActivity(intent);true}
        catch(_:ActivityNotFoundException){false}
        catch(_:SecurityException){false}
    }
}

/** Returns true only when the WebView navigation was consumed (launch, prompt or fallback). */
internal fun BrowserController.openExternalLink(tab:BrowserTab,raw:String,allowed:Boolean):Boolean {
    val link=ExternalLinks.parse(raw)?:return false
    if(!allowed||tab !in tabs||tab.id!=activeId||tab.web.selecting)return false
    if(link.webLink&&!ExternalLinks.hasDefaultWebApp(context,link.intent))return false
    val page=tab.url
    val generation=tab.navigationGeneration
    val discardEmptyPopup={
        tab.externalOpenerId?.let{opener->
            if(tab in tabs&&tab.url.isEmpty()){closeTab(tab.id);if(tabs.any{it.id==opener})switchTab(opener)}
        }
    }
    val onLaunched={
        tab.pendingUrl="";tab.refreshContainer.isRefreshing=false
        // A target=_blank link must not leave an empty throwaway tab behind.
        discardEmptyPopup()
    }
    val launch={
        if(tab in tabs&&tab.id==activeId&&tab.url==page&&tab.navigationGeneration==generation&&!tab.web.selecting){
            tab.externalGesture.reset()
            if(ExternalLinks.launch(context,link)){
                onLaunched()
            }else if(link.fallback!=null){
                tab.externalOpenerId=null
                tab.pendingUrl=link.fallback;tab.web.loadUrl(link.fallback)
            }else {discardEmptyPopup();notice="找不到可開啟此連結的 App，請確認已安裝對應的應用程式"}
        }
    }
    if(tab.incognito){
        val activity=context as? Activity?:return false
        if(activity.isFinishing||activity.isDestroyed)return false
        AlertDialog.Builder(activity).setTitle("在其他 App 開啟？")
            .setMessage("將離開澄境的無痕瀏覽。其他 App 可能保存這個連結與使用記錄。")
            .setNegativeButton("取消"){_,_->discardEmptyPopup()}.setOnCancelListener{discardEmptyPopup()}
            .setPositiveButton("開啟"){_,_->launch()}
            .create().also{it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE);it.show()}
        return true
    }
    // An ordinary HTTPS URL that fails to open in its app must continue in WebView.
    if(link.webLink){
        tab.externalGesture.reset()
        return ExternalLinks.launch(context,link).also{if(it)onLaunched()}
    }
    launch()
    return true
}
