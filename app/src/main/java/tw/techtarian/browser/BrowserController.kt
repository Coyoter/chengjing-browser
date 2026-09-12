package tw.techtarian.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.webkit.*
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class Selection(val selector: String, val label: String, val count: Int, val width: Int, val height: Int, val canParent: Boolean, val frame: Boolean)
class BrowserTab(val id: Int, val web: SelectionWebView) {
    val refreshContainer = RefreshWebContainer(web.context, web)
    var url by mutableStateOf("")
    var title by mutableStateOf("新分頁")
    var progress by mutableIntStateOf(100)
    var error by mutableStateOf("")
    var certificateWarning by mutableStateOf("")
    @Volatile var pendingUrl = ""
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var documentScript: ScriptHandler? = null
    var documentConfig: String? = null
    var blockedUrl by mutableStateOf("")
    var favoriteId by mutableStateOf<String?>(null)
    var favoriteRestore:Favorite?=null
    var favoriteRestoreTouchSequence=0L
    var blockedTotal by mutableIntStateOf(0)
    var blockedUnread by mutableStateOf(false)
    val blockedEvents=mutableStateListOf<BlockedEvent>()
}

class BrowserController(val context: Context, val store: BrowserStore) {
    val favorites=FavoriteStore(context)
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableIntStateOf(0)
    val active: BrowserTab? get() = tabs.find { it.id == activeId }
    var revision by mutableIntStateOf(0)
    var eye by mutableStateOf(false)
    var selection by mutableStateOf<Selection?>(null)
    var draft by mutableStateOf<SiteRules?>(null)
    var dirty by mutableStateOf(false)
    var notice by mutableStateOf("")
    var sheet by mutableStateOf("")
    var blockedCount by mutableIntStateOf(0)
    val exceptions = mutableStateListOf<String>()
    var fileCallback: ValueCallback<Array<Uri>>? = null
    var chooseFiles: ((Intent) -> Unit)? = null
    var fullScreenView by mutableStateOf<android.view.View?>(null)
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var nextId = 1
    private val mainHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val script = context.assets.open("skyeye.js").bufferedReader().readText()
    val domain: String get() = Domains.scope(active?.url.orEmpty())
    val site: SiteRules get() { revision; return store.get(domain) }
    val isException: Boolean get() = domain in exceptions
    val isSupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun config(): String = JSONObject().put("sites", JSONArray(store.all().map { it.json() })).put("exceptions", JSONArray(exceptions)).toString()
    fun refreshScripts(targetTabs:List<BrowserTab> = tabs.toList(),applyToPage:Boolean=true) {
        val configuration=config()
        val documentStart=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        val source=if(documentStart)script.replace("__CJ_CONFIG__",configuration)else ""
        tabs.forEach { tab ->
            if(documentStart && tab.documentConfig!=configuration){
                tab.documentScript?.remove()
                tab.documentScript=WebViewCompat.addDocumentStartJavaScript(tab.web,source,setOf("*"))
                tab.documentConfig=configuration
            }
            if(applyToPage && tab in targetTabs && tab.url.isNotEmpty())tab.web.evaluateJavascript("window.__chengjingEye?.configure($configuration)",null)
        }
        revision++
    }
    private fun reloadSite(domain:String){
        val affected=tabs.filter{Domains.scope(it.url)==domain}
        // Retire the old document instead of reconfiguring its DOM immediately before unloading it.
        affected.forEach{it.web.selecting=false;it.refreshContainer.isEnabled=true;it.refreshContainer.isRefreshing=false;it.web.stopLoading();it.error=""}
        refreshScripts(affected,applyToPage=false)
        affected.forEach{it.pendingUrl=it.url;it.web.reload()}
    }
    fun persistTabs() = store.saveTabs(tabs.map { it.url },tabs.map{it.favoriteId})
    fun currentUserAgent(desktop:Boolean=false):String{
        val original=WebSettings.getDefaultUserAgent(context)
        return when{
            desktop->BrowserUserAgent.desktop(original)
            store.userAgentMode=="webview"->original
            store.userAgentMode=="custom"&&store.customUserAgent.isNotBlank()->store.customUserAgent
            else->BrowserUserAgent.mobile(original)
        }
    }
    fun setUserAgent(mode:String,custom:String){
        require(mode in setOf("chrome","webview","custom"))
        if(mode=="custom")require(custom.isNotBlank()&&custom.length<=1024&&custom.all{it.code in 32..126}){"請使用單行英文字母、數字與符號，最多 1,024 字"}
        store.userAgentMode=mode;store.customUserAgent=custom.trim()
        active?.desktop=false
        tabs.forEach{it.web.settings.userAgentString=currentUserAgent(it.desktop)}
        revision++;reload()
    }
    private fun recordBlocked(tab:BrowserTab,kind:String,url:String=""){
        blockedCount++;tab.blockedTotal++;tab.blockedUnread=true
        tab.blockedEvents.add(0,BlockedEvent(kind,url.take(4000)))
        if(tab.blockedEvents.size>30)tab.blockedEvents.removeAt(tab.blockedEvents.lastIndex)
    }
    fun toggleDesktop(){
        active?.let{tab->
            tab.desktop=!tab.desktop
            tab.web.settings.userAgentString=currentUserAgent(tab.desktop)
            reload()
        }
    }
    fun restoreTabs() {
        val saved=store.tabs();val links=store.tabFavoriteLinks()
        if(saved.isEmpty())newTab()else saved.forEachIndexed{i,url->
            val link=links.getOrNull(i)?.takeIf{it.first==url}?.second?.let{favorites.get(it)}
            newTab(url)?.favoriteId=link?.takeIf{Domains.scope(it.url)==Domains.scope(url)}?.id
        }
        persistTabs()
    }
    @SuppressLint("SetJavaScriptEnabled")
    fun newTab(url: String = ""): BrowserTab? {
        if(tabs.size>=20){notice="目前最多可開啟 20 個分頁，請先關閉不用的分頁";return null}
        stopEye()
        val web=SelectionWebView(context)
        val tab=BrowserTab(nextId++,web)
        tab.refreshContainer.setOnRefreshListener {
            if (web.selecting || tab !in tabs) tab.refreshContainer.isRefreshing=false
            else { tab.error=""; web.reload() }
        }
        web.setBackgroundColor(android.graphics.Color.WHITE)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true
            userAgentString=currentUserAgent()
            allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true);javaScriptCanOpenWindowsAutomatically=true
            loadWithOverviewMode=true;useWideViewPort=true
            builtInZoomControls=true;displayZoomControls=false
            safeBrowsingEnabled=true;mediaPlaybackRequiresUserGesture=true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // Deliberately read-only messages. Page code cannot save rules, open URLs or access the key.
            WebViewCompat.addWebMessageListener(web,"ChengJingSelection",setOf("*")) { _, message, origin, mainFrame, _ ->
                if(!mainFrame || activeId!=tab.id || !Domains.matches(origin.host.orEmpty(),Domains.scope(tab.url))) return@addWebMessageListener
                val raw=message.data ?: return@addWebMessageListener
                if(raw.length>6000)return@addWebMessageListener
                runCatching {
                    val j=JSONObject(raw)
                    when(j.optString("type")) {
                        "selection" -> if(eye && RuleValidation.selectorError(j.optString("selector"))==null) {
                            selection=Selection(j.getString("selector"),j.optString("label").take(180),j.optInt("count"),j.optInt("width"),j.optInt("height"),j.optBoolean("canParent"),j.optBoolean("frame"))
                            sheet="selection"
                        }
                        "codeError" -> { notice="自訂程式執行失敗；可在天眼設定修改或切換例外" }
                    }
                }
            }
        }
        web.webViewClient=object:WebViewClient(){
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                val page=if(request.isForMainFrame)request.url.toString()else tab.pendingUrl.ifBlank{tab.url}
                CertificateWarnings.session.carryKnownResource(page,request.url.toString())
                if(CertificateWarnings.session.messageFor(page).isNotEmpty())mainHandler.post{
                    if(tab in tabs)tab.certificateWarning=CertificateWarnings.session.messageFor(tab.url.ifBlank{tab.pendingUrl})
                }
                // POST navigations skip shouldOverrideUrlLoading. HTTP 204 keeps the existing document.
                if(web.selecting && request.isForMainFrame) return WebResourceResponse("text/plain","UTF-8",204,"No Content",emptyMap(),java.io.ByteArrayInputStream(byteArrayOf()))
                if(request.url.host=="practice.chengjing.invalid" && request.isForMainFrame) return WebResourceResponse("text/html","UTF-8",context.assets.open("practice.html"))
                return null
            }
            override fun shouldOverrideUrlLoading(view:WebView, request:WebResourceRequest):Boolean {
                if(web.selecting) return true
                val u=request.url.toString()
                if(request.url.scheme !in listOf("https","http")) {
                    if(request.isForMainFrame)recordBlocked(tab,"外部 App 跳轉",u)
                    return true
                }
                if(!request.isForMainFrame)return false
                val scope=Domains.scope(tab.url)
                if(scope !in exceptions && store.get(scope).guard && !request.hasGesture() && !request.isRedirect && tab.url.isNotEmpty()) {
                    tab.blockedUrl=u;recordBlocked(tab,"自動跳轉",u);return true
                }
                tab.pendingUrl=u
                return false
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?) {
                if(web.selecting){view.stopLoading();return}
                tab.url=url;tab.pendingUrl=url;tab.error="";tab.blockedUrl=""
                tab.favoriteId?.let{id->
                    if(favorites.get(id)?.let{Domains.scope(it.url)!=Domains.scope(url)}!=false){tab.favoriteId=null;tab.favoriteRestore=null}
                }
                tab.certificateWarning=CertificateWarnings.session.messageFor(url)
                if(activeId==tab.id){eye=false;selection=null;draft=null;dirty=false;if(sheet=="selection")sheet=""}
                persistTabs()
            }
            override fun doUpdateVisitedHistory(view:WebView,url:String,isReload:Boolean){
                if(url.startsWith("http://")||url.startsWith("https://")){
                    tab.url=url;tab.canBack=view.canGoBack();tab.canForward=view.canGoForward();persistTabs()
                }
            }
            override fun onPageFinished(view:WebView,url:String) {
                tab.refreshContainer.isRefreshing=false
                tab.canBack=view.canGoBack();tab.canForward=view.canGoForward()
                tab.pendingUrl=""
                tab.certificateWarning=CertificateWarnings.session.messageFor(tab.url)
                tab.favoriteRestore?.let{favorite->
                    tab.favoriteRestore=null
                    val touches=tab.favoriteRestoreTouchSequence
                    var attempts=0
                    val restore=object:Runnable{
                        override fun run(){
                            if(tab !in tabs || tab.favoriteId!=favorite.id || web.touchSequence!=touches || web.selecting)return
                            if(!web.isAttachedToWindow||web.width==0||web.height==0){if(attempts++<30)mainHandler.postDelayed(this,100);return}
                            val code="if(location.href===${JSONObject.quote(favorite.url)}){window.scrollTo(0,${favorite.scrollY})}"
                            web.evaluateJavascript(code,null)
                        }
                    }
                    mainHandler.postDelayed(restore,350)
                }
                if(tab.error.isEmpty()) store.visit(url,tab.title)
                // Fallback remains usable on older WebView; document-start protection requires an update.
                if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))view.evaluateJavascript(script.replace("__CJ_CONFIG__",config()),null)
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(web.selecting)return
                if(request.isForMainFrame){tab.refreshContainer.isRefreshing=false;tab.error=if(error.errorCode==ERROR_FAILED_SSL_HANDSHAKE)"這個網站無法完成加密連線，請稍後重試。"else"網頁暫時無法開啟，請確認網路或網址後重試。";tab.progress=100}
            }
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError){
                val reason=when(error.primaryError){
                    android.net.http.SslError.SSL_EXPIRED->"憑證已過期"
                    android.net.http.SslError.SSL_NOTYETVALID->"憑證尚未生效"
                    android.net.http.SslError.SSL_IDMISMATCH->"憑證網域不符"
                    android.net.http.SslError.SSL_UNTRUSTED->"憑證來源不受信任"
                    android.net.http.SslError.SSL_DATE_INVALID->"憑證日期異常"
                    else->"憑證驗證未通過"
                }
                val page=tab.pendingUrl.ifBlank{tab.url.ifBlank{view.url.orEmpty()}}
                CertificateWarnings.session.record(page,error.url,reason)
                tabs.forEach{it.certificateWarning=CertificateWarnings.session.messageFor(it.url.ifBlank{it.pendingUrl})}
                tab.certificateWarning=CertificateWarnings.session.messageFor(page.ifBlank{error.url})
                // Explicit personal-browser preference: continue recoverable certificate errors,
                // with a persistent warning. Google Drive / OpenRouter clients keep strict TLS.
                handler.proceed()
            }
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {
                tab.refreshContainer.isRefreshing=false
                tab.error="網頁程序已停止，請關閉這個分頁後重新開啟。";return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,value:Int){tab.progress=value}
            override fun onReceivedTitle(view:WebView,title:String?){tab.title=title?.take(180)?:tab.url}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(web.selecting) return false
                val scope=Domains.scope(tab.url)
                if(!isUserGesture || (scope !in exceptions && store.get(scope).guard)) {recordBlocked(tab,"新視窗／彈窗");return false}
                val child=newTab() ?: return false
                (resultMsg.obj as WebView.WebViewTransport).webView=child.web;resultMsg.sendToTarget();return true
            }
            override fun onPermissionRequest(request:PermissionRequest){request.deny();notice="此版本尚未開放網站使用相機與麥克風"}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false);notice="此版本尚未開放網站定位"}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                if(web.selecting){callback.onReceiveValue(null);return true}
                fileCallback?.onReceiveValue(null);fileCallback=callback
                runCatching { chooseFiles?.invoke(params.createIntent()) ?: error("檔案選擇器不可用") }.onFailure {fileCallback?.onReceiveValue(null);fileCallback=null;notice="無法開啟檔案選擇器"};return true
            }
            override fun onShowCustomView(view:android.view.View,callback:CustomViewCallback){fullScreenView=view;fullScreenCallback=callback}
            override fun onHideCustomView(){exitFullscreen()}
        }
        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if(web.selecting)return@setDownloadListener
            if(!url.startsWith("https://")&&!url.startsWith("http://")){notice="此類型的下載尚未支援";return@setDownloadListener}
            val name=URLUtil.guessFileName(url,disposition,mime)
            android.app.AlertDialog.Builder(context).setTitle("下載檔案？").setMessage(name).setNegativeButton("取消",null).setPositiveButton("下載") { _,_->
                runCatching {
                    val req=DownloadManager.Request(Uri.parse(url)).setTitle(name).setMimeType(mime).addRequestHeader("User-Agent",userAgent).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
                    CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie",it) }
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    notice="開始下載，可在系統下載項目查看"
                }.onFailure {notice="下載未完成：${it.localizedMessage}"}
            }.show()
        }
        web.setOnLongClickListener {
            val hit=web.hitTestResult
            val link=hit.extra
            if(!eye && hit.type in listOf(WebView.HitTestResult.SRC_ANCHOR_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && link!=null) {
                android.app.AlertDialog.Builder(context).setItems(arrayOf("在新分頁開啟","複製連結")) {_,which->if(which==0)newTab(link)else{(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("連結",link))}}.show();true
            } else false
        }
        tabs.add(tab);activeId=tab.id
        refreshScripts(listOf(tab),applyToPage=false)
        if(url.isNotEmpty()){tab.pendingUrl=url;web.loadUrl(url)}
        persistTabs();return tab
    }
    fun exitFullscreen(){fullScreenView=null;fullScreenCallback?.onCustomViewHidden();fullScreenCallback=null}
    fun navigate(input:String,fromFavorite:Favorite?=null) {
        val url=Domains.address(input);if(url.isEmpty())return
        stopEye();sheet="";active?.error="";active?.favoriteId=fromFavorite?.id;active?.favoriteRestore=fromFavorite;active?.favoriteRestoreTouchSequence=active?.web?.touchSequence?:0L;active?.pendingUrl=url;active?.web?.loadUrl(url)
    }
    fun openFavorite(favorite:Favorite){navigate(favorite.url,favorite)}
    fun removeFavorite(id:String){favorites.remove(id);tabs.filter{it.favoriteId==id}.forEach{it.favoriteId=null;it.favoriteRestore=null};revision++;persistTabs()}
    suspend fun saveFavorite(forceNew:Boolean=false):Favorite?{
        val tab=active?:return null
        if(domain.isEmpty()){notice="先開啟網頁，再加入收藏";return null}
        val expected=tab.web.url
        val raw=js("JSON.stringify({url:location.href,title:document.title,y:scrollY,progress:scrollY/Math.max(1,document.documentElement.scrollHeight-innerHeight)})")
        if(activeId!=tab.id || tab.web.url!=expected){notice="頁面已變更，請再試一次";return null}
        return runCatching{
            val data=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
            val favorite=favorites.save(data.getString("url"),data.optString("title",tab.title),data.optDouble("y",0.0),data.optDouble("progress",0.0),tab.favoriteId,forceNew)
            tab.favoriteId=favorite.id;revision++;persistTabs();favorite
        }.onFailure{notice="收藏未完成：${it.localizedMessage}"}.getOrNull()
    }
    fun reload(){stopEye();active?.web?.reload()}
    fun switchTab(id:Int){stopEye();activeId=id;sheet=""}
    fun closeTab(id:Int){
        if(id==activeId)stopEye()
        val tab=tabs.find{it.id==id}?:return
        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)
        if(activeId==id)activeId=tabs.lastOrNull()?.id?:0
        if(tabs.isEmpty())newTab()
        persistTabs()
    }
    fun beginEye(){
        if(domain.isEmpty()){notice="先開啟一個網站，再使用天眼";return}
        if(isException){notice="目前正在顯示原始網站，請先結束例外";return}
        if(!isSupported){notice="請先在 Play 商店更新 Android System WebView，才能啟用完整天眼";return}
        if(!eye){draft=site;dirty=false}
        eye=true;selection=null;sheet="";active?.web?.selecting=true
        active?.refreshContainer?.isEnabled=false;active?.refreshContainer?.isRefreshing=false
        active?.web?.evaluateJavascript("window.__chengjingEye?.enable(true)",null)
    }
    fun stopEye(applyToPage:Boolean=true){
        val wasSelecting=eye || active?.web?.selecting==true || draft!=null
        active?.web?.selecting=false;active?.refreshContainer?.isEnabled=true
        eye=false;selection=null;draft=null;dirty=false
        if(wasSelecting && applyToPage)active?.web?.evaluateJavascript("window.__chengjingEye?.enable(false);window.__chengjingEye?.configure(${config()})",null)
    }
    fun parentSelection(){sheet="";active?.web?.evaluateJavascript("window.__chengjingEye?.parent()",null)}
    fun chooseSelector(selector:String){sheet="";active?.web?.evaluateJavascript("window.__chengjingEye?.select(${JSONObject.quote(selector)})",null)}
    suspend fun js(expression:String):String = suspendCancellableCoroutine { c ->
        val web=active?.web
        if(web==null){c.resume("null");return@suspendCancellableCoroutine}
        web.evaluateJavascript(expression){if(c.isActive)c.resume(it?:"null")}
    }
    suspend fun validateSelectors(selectors:List<String>):String? {
        for(s in selectors){
            RuleValidation.selectorError(s)?.let{return it}
            val result=js("window.__chengjingEye?.inspect(${JSONObject.quote(s)})")
            val j=runCatching{JSONObject(result)}.getOrNull()?:return "網頁尚未準備好，請重新載入後再試"
            if(!j.optBoolean("valid"))return j.optString("error","無法使用這條規則")
            if(j.optInt("count")==0)return "目前頁面找不到 $s，請重新選取元件"
        }
        return null
    }
    fun previewSite(value:SiteRules){draft=value;dirty=true;active?.web?.evaluateJavascript("window.__chengjingEye?.preview(${value.json()})",null)}
    suspend fun removeSelected():Boolean {
        val s=selection?:return false
        validateSelectors(listOf(s.selector))?.let{notice=it;return false}
        val value=draft?:site
        previewSite(value.copy(rules=(value.rules+ElementRule(s.selector,s.label)).distinctBy{it.selector}))
        selection=null;sheet="";notice="已預覽移除；確認後按「儲存」";return true
    }
    fun undoDraft(){draft?.let{previewSite(it.copy(rules=it.rules.dropLast(1)))};selection=null;sheet=""}
    fun saveDraft(){val d=draft?:return;saveSite(d);stopEye();notice="已儲存，將套用 ${d.domain} 及其子網域"}
    fun saveSite(site:SiteRules,reload:Boolean=false){
        store.save(site)
        if(reload)reloadSite(site.domain)else refreshScripts(tabs.filter{Domains.scope(it.url)==site.domain})
        revision++
    }
    fun exception(){
        val d=domain;if(d.isEmpty())return
        stopEye(applyToPage=false)
        if(d in exceptions)exceptions.remove(d)else exceptions.add(d)
        reloadSite(d)
        notice=if(d in exceptions)"已暫時顯示原始網站；規則仍然保留"else"已恢復套用天眼規則"
    }
    fun restoreRules(domain:String){if(store.undo(domain)){reloadSite(domain);notice="已復原上一次儲存"}}
    fun destroy(){fileCallback?.onReceiveValue(null);tabs.forEach{(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear()}
}
