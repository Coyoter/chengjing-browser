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
class BrowserTab(val id:Int,val web:SelectionWebView,val incognito:Boolean=false,
    val previewKey:String?=if(incognito)null else TabPreviewStore.newKey()) {
    init{require(if(incognito)previewKey==null else TabPreviewStore.validKey(previewKey))}
    internal var previewReady=false
    internal var navigationGeneration=0L
    internal var previewGeneration=0L
    internal var lastActiveAt=System.currentTimeMillis()
    internal var restoringNavigation=false
    internal var suppressHistoryUntilNavigation=false
    internal val externalGesture=ExternalLinkGesture{android.os.SystemClock.elapsedRealtime()}
    internal var externalOpenerId:Int?=null
    var preview by mutableStateOf<Bitmap?>(null)
    val warnings=if(incognito)CertificateWarnings()else CertificateWarnings.session
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
    internal var developerSuggestion:suspend (String,String,SiteRules,String?)->DeveloperProposal = {problem,structure,current,selected->if(store.aiProvider=="gemma")gemma.develop(problem,structure,current,selected)else OpenRouter().develop(store.readKey(),store.model,problem,structure,current,selected)}
    val gemma=GemmaLocal(context.applicationContext)
    val icons=SiteIcons(context.applicationContext)
    val favorites=FavoriteStore(context)
    internal val home=HomePreferences(context,existingUser=store.tabs().isNotEmpty()||store.history().isNotEmpty())
    internal val homeQuotes by lazy{context.assets.open("home-quotes.txt").bufferedReader(Charsets.UTF_8).use{it.readLines()}.filter{it.isNotBlank()}}
    internal val previews=TabPreviewStore(context)
    internal val browsingDataCleaner=BrowsingDataCleaner(this)
    private var restoringTabs=false
    private var destroyed=false
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableIntStateOf(0)
    var overviewPrivate by mutableStateOf(false)
    internal val privateSession=PrivateSession{notice=it}
    val privateScreen:Boolean get()=active?.incognito==true||(sheet=="tabs"&&overviewPrivate)
    val activeTabCount:Int get()=tabs.count{it.incognito==(active?.incognito==true)}
    fun updatePrivacyWindow(){
        val window=(context as? android.app.Activity)?.window?:return
        if(privateScreen)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun openTabOverview(){capturePreview(active);overviewPrivate=active?.incognito==true;sheet="tabs";updatePrivacyWindow()}
    private fun capturePreview(tab:BrowserTab?){
        if(destroyed||restoringTabs||tab==null||tab !in tabs||tab.incognito||!tab.previewReady||tab.error.isNotEmpty()||
            tab.url.isEmpty()||tab.pendingUrl.isNotEmpty()||tab.id!=activeId||tab.web.selecting||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return
        runCatching{
            val width=300
            val height=(tab.web.height.toFloat()*width/tab.web.width).toInt().coerceIn(1,400)
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            canvas.scale(width.toFloat()/tab.web.width,width.toFloat()/tab.web.width)
            tab.web.draw(canvas)
            tab.previewGeneration++
            tab.preview=bitmap
            previews.save(tab.previewKey,bitmap){if(!destroyed)notice="分頁快照未能儲存，既有快照已保留"}
        }
    }
    private fun requestPreviewFrame(tab:BrowserTab){
        if(destroyed||tab.incognito||tab !in tabs||tab.url.isEmpty()||tab.pendingUrl.isNotEmpty()||tab.error.isNotEmpty())return
        val generation=tab.navigationGeneration
        tab.web.postVisualStateCallback(generation,object:WebView.VisualStateCallback(){
            override fun onComplete(requestId:Long){
                if(!destroyed&&tab in tabs&&generation==tab.navigationGeneration&&tab.pendingUrl.isEmpty()&&tab.error.isEmpty()){
                    tab.previewReady=true
                    capturePreview(tab)
                }
            }
        })
    }
    fun checkpointTabs(){
        if(destroyed||restoringTabs)return
        capturePreview(active)
        persistTabs()
        if(!previews.flush())notice="快照仍在儲存中，請稍後再關閉應用程式"
    }
    internal fun cookiesFor(tab:BrowserTab)=if(tab.incognito)privateSession.cookies(tab.web)else CookieManager.getInstance()
    internal fun downloadFor(tab:BrowserTab,url:String,page:String,agent:String,mime:String?=null,disposition:String?=null,imageOnly:Boolean=true){
        if(tab !in tabs)return
        val action={
            if(tab in tabs)(context as? MainActivity)?.imageDownloads?.download(url,page,agent,
                cookieHeader=cookiesFor(tab).getCookie(url),mimeHint=mime,disposition=disposition,imageOnly=imageOnly)
            Unit
        }
        if(tab.incognito)android.app.AlertDialog.Builder(context).setTitle("下載會保留在手機")
            .setMessage("檔案與系統下載紀錄不會隨無痕分頁清除。確定要下載嗎？")
            .setNegativeButton("取消",null).setPositiveButton("下載"){_,_->action()}.show()
        else action()
    }
    val active: BrowserTab? get() = tabs.find { it.id == activeId }
    var revision by mutableIntStateOf(0)
    var eye by mutableStateOf(false)
    var selection by mutableStateOf<Selection?>(null)
    var draft by mutableStateOf<SiteRules?>(null)
    var dirty by mutableStateOf(false)
    var notice by mutableStateOf("")
    var sheet by mutableStateOf("")
    var aiElement by mutableStateOf<Selection?>(null)
    var editingHtml by mutableStateOf(false)
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

    fun config(page:String?=null): String {
        val host=page?.let{Uri.parse(it).host.orEmpty()}
        val sites=store.all().filter{host==null||Domains.matches(host,it.domain)}
        val bypass=exceptions.filter{host==null||Domains.matches(host,it)}
        return JSONObject().put("sites",JSONArray(sites.map{it.json()})).put("exceptions",JSONArray(bypass)).toString()
    }
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
            if(applyToPage && tab in targetTabs && tab.url.isNotEmpty())tab.web.evaluateJavascript("window.__chengjingEye?.configure(${config(tab.url)})",null)
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
    fun persistTabs():Boolean {
        if(destroyed||restoringTabs)return false
        val normal=tabs.filterNot{it.incognito}
        val saved=store.saveTabs(normal.map{it.url},normal.map{it.favoriteId},normal.map{it.previewKey!!},normal.map{it.title},normal.map{it.lastActiveAt})
        if(!saved)notice="分頁資料未能儲存，請檢查手機儲存空間"
        return saved
    }
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
        if(tabs.isNotEmpty())return
        val saved=store.savedTabRecords()
        // A partially rebuilt list must never replace/prune the not-yet-restored tabs.
        restoringTabs=true
        try{
            if(saved.isEmpty())newTab(incognito=false)else saved.forEach{record->
                createTab(record.url,false,record)
            }
        }finally{restoringTabs=false}
        if(persistTabs())previews.retainOnly(tabs.mapNotNull{it.previewKey}.toSet())
    }
    fun newTab(url:String="",incognito:Boolean=active?.incognito==true):BrowserTab?=createTab(url,incognito,null)
    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url:String,incognito:Boolean,restored:SavedBrowserTab?):BrowserTab? {
        if(incognito&&!privateSession.supported){sheet="";notice="請更新 Android System WebView，才能使用資料隔離的無痕分頁";return null}
        if(tabs.size>=20){notice="目前最多可開啟 20 個分頁，請先關閉不用的分頁";return null}
        capturePreview(active)
        stopEye()
        val web=SelectionWebView(context)
        if(incognito)try{privateSession.attach(web)}catch(_:Exception){web.destroy();notice="無法建立隔離的無痕工作階段，未開啟網頁";return null}
        // Discard WebView trust decisions from older versions; only saved site choices apply.
        web.clearSslPreferences()
        val tab=BrowserTab(nextId++,web,incognito,if(incognito)null else restored?.key?:TabPreviewStore.newKey())
        tab.url=url
        restored?.let{record->
            tab.lastActiveAt=record.lastActiveAt
            tab.restoringNavigation=url.isNotEmpty()
            tab.title=record.title.ifBlank{"新分頁"}
            tab.favoriteId=record.favoriteId?.let{favorites.get(it)}?.takeIf{Domains.scope(it.url)==Domains.scope(url)}?.id
            val generation=tab.previewGeneration
            previews.load(tab.previewKey){bitmap->
                if(!destroyed&&tab in tabs&&tab.preview==null&&generation==tab.previewGeneration)tab.preview=bitmap
                else bitmap?.recycle()
            }
        }
        web.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(view:android.view.View){requestPreviewFrame(tab)}
            override fun onViewDetachedFromWindow(view:android.view.View){}
        })
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
        if(incognito){
            web.settings.cacheMode=WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION")
            web.settings.saveFormData=false
            web.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            if(android.os.Build.VERSION.SDK_INT>=30)web.importantForContentCapture=android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        cookiesFor(tab).setAcceptThirdPartyCookies(web,false)
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
                tab.warnings.carryKnownResource(page,request.url.toString())
                if(tab.warnings.messageFor(page).isNotEmpty())mainHandler.post{
                    if(tab in tabs)tab.certificateWarning=tab.warnings.messageFor(tab.url.ifBlank{tab.pendingUrl})
                }
                // POST navigations skip shouldOverrideUrlLoading. HTTP 204 keeps the existing document.
                if(web.selecting && request.isForMainFrame) return WebResourceResponse("text/plain","UTF-8",204,"No Content",emptyMap(),java.io.ByteArrayInputStream(byteArrayOf()))
                if(request.url.host=="practice.chengjing.invalid" && request.isForMainFrame) return WebResourceResponse("text/html","UTF-8",context.assets.open("practice.html"))
                return null
            }
            override fun shouldOverrideUrlLoading(view:WebView, request:WebResourceRequest):Boolean {
                if(tab !in tabs)return true
                if(web.selecting) return true
                val u=request.url.toString()
                // The native homepage has an actual history entry, so Back/Forward stay useful.
                if(request.isForMainFrame&&u=="about:blank")return false
                val allowed=tab.externalGesture.allow(request.isForMainFrame,request.hasGesture(),request.isRedirect)
                if(!ExternalLinkPolicy.isWeb(request.url.scheme)) {
                    if(openExternalLink(tab,u,allowed))return true
                    if(request.isForMainFrame){recordBlocked(tab,"外部 App 跳轉",u);if(allowed)notice="此連結格式無法開啟"}
                    return true
                }
                if(!request.isForMainFrame)return false
                val scope=Domains.scope(tab.url)
                if(scope !in exceptions && store.get(scope).guard && !request.hasGesture() && !request.isRedirect && tab.url.isNotEmpty()) {
                    tab.blockedUrl=u;recordBlocked(tab,"自動跳轉",u);return true
                }
                if(openExternalLink(tab,u,allowed))return true
                tab.pendingUrl=u
                return false
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?) {
                if(tab !in tabs)return
                if(web.selecting){view.stopLoading();return}
                if(!tab.restoringNavigation)tab.lastActiveAt=System.currentTimeMillis()
                tab.suppressHistoryUntilNavigation=false
                tab.navigationGeneration++
                tab.previewReady=false
                // Keep the last successful thumbnail during reload, offline restore and errors.
                tab.url=if(url=="about:blank")""else url
                tab.pendingUrl=url;tab.error="";tab.blockedUrl=""
                if(url=="about:blank"){
                    tab.title="澄境首頁";tab.previewGeneration++;tab.preview=null
                    previews.remove(tab.previewKey)
                }
                tab.favoriteId?.let{id->
                    if(favorites.get(id)?.let{Domains.scope(it.url)!=Domains.scope(url)}!=false){tab.favoriteId=null;tab.favoriteRestore=null}
                }
                tab.certificateWarning=tab.warnings.messageFor(url)
                if(activeId==tab.id){eye=false;selection=null;draft=null;dirty=false;if(sheet=="selection")sheet=""}
                persistTabs()
            }
            override fun doUpdateVisitedHistory(view:WebView,url:String,isReload:Boolean){
                if(tab !in tabs)return
                if(url=="about:blank"||url.startsWith("http://")||url.startsWith("https://")){
                    if(!tab.restoringNavigation&&url!=tab.url)tab.lastActiveAt=System.currentTimeMillis()
                    tab.url=if(url=="about:blank")""else url
                    tab.canBack=view.canGoBack();tab.canForward=view.canGoForward();persistTabs()
                }
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(tab !in tabs)return
                tab.externalGesture.reset()
                tab.externalOpenerId=null
                tab.refreshContainer.isRefreshing=false
                tab.canBack=view.canGoBack();tab.canForward=view.canGoForward()
                tab.pendingUrl=""
                tab.certificateWarning=tab.warnings.messageFor(tab.url)
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
                if(tab.error.isEmpty()&&!tab.incognito&&!tab.restoringNavigation&&!tab.suppressHistoryUntilNavigation){store.visit(url,tab.title);revision++}
                tab.restoringNavigation=false
                requestPreviewFrame(tab)
                // Fallback remains usable on older WebView; document-start protection requires an update.
                if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))view.evaluateJavascript(script.replace("__CJ_CONFIG__",config(tab.url)),null)
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
                tab.warnings.record(page,error.url,reason)
                tabs.filter{it.incognito==tab.incognito}.forEach{it.certificateWarning=it.warnings.messageFor(it.url.ifBlank{it.pendingUrl})}
                tab.certificateWarning=tab.warnings.messageFor(page.ifBlank{error.url})
                if(store.certificateException(error.url))handler.proceed()
                else {
                    handler.cancel()
                    if(CertificateExceptions.sameDocument(page,error.url)){
                        // A failed first navigation may never receive onPageStarted. Keep its URL accessible.
                        tab.url=page;persistTabs()
                        tab.error="這個網站的憑證未通過驗證，連線已停止。可點網址列的連線圖示查看原因與網站例外設定。";tab.progress=100;tab.refreshContainer.isRefreshing=false
                    }
                }
            }
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {
                tab.refreshContainer.isRefreshing=false
                tab.error="網頁程序已停止，請關閉這個分頁後重新開啟。";return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,value:Int){tab.progress=value}
            override fun onReceivedIcon(view:WebView,icon:Bitmap?){if(icon!=null&&!tab.incognito)view.url?.let{icons.remember(it,icon)}}
            override fun onReceivedTitle(view:WebView,title:String?){
                tab.title=if(tab.url.isEmpty()&&view.url=="about:blank")"澄境首頁"else title?.take(180)?:tab.url
                persistTabs()
            }
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(web.selecting) return false
                val scope=Domains.scope(tab.url)
                if(!isUserGesture || (scope !in exceptions && store.get(scope).guard)) {recordBlocked(tab,"新視窗／彈窗");return false}
                val child=newTab(incognito=tab.incognito) ?: return false
                child.externalGesture.popupFromClick();child.externalOpenerId=tab.id
                (resultMsg.obj as WebView.WebViewTransport).webView=child.web;resultMsg.sendToTarget();return true
            }
            override fun onPermissionRequest(request:PermissionRequest){request.deny();notice="此版本尚未開放網站使用相機與麥克風"}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false);notice="此版本尚未開放網站定位"}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                if(web.selecting){callback.onReceiveValue(null);return true}
                fileCallback?.onReceiveValue(null);fileCallback=callback
                runCatching { chooseFiles?.invoke(params.createIntent()) ?: error("檔案選擇器不可用") }.onFailure {fileCallback?.onReceiveValue(null);fileCallback=null;notice="無法開啟檔案選擇器"};return true
            }
            override fun onShowCustomView(view:android.view.View,callback:CustomViewCallback){if(fullScreenView!=null){callback.onCustomViewHidden();return};stopEye();sheet="";(context as? android.app.Activity)?.let{activity->activity.currentFocus?.clearFocus();(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)};fullScreenView=view;fullScreenCallback=callback}
            override fun onHideCustomView(){exitFullscreen()}
        }
        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if(web.selecting)return@setDownloadListener
            if(!url.startsWith("https://")&&!url.startsWith("http://")){notice="此類型的下載尚未支援";return@setDownloadListener}
            val name=URLUtil.guessFileName(url,disposition,mime)
            android.app.AlertDialog.Builder(context).setTitle("下載檔案？").setMessage(name).setNegativeButton("取消",null).setPositiveButton("下載") { _,_->
                downloadFor(tab,url,tab.url,userAgent,mime,disposition,imageOnly=false)
            }.show()
        }
        PageContextMenu(this,tab).install()
        tabs.add(tab);activeId=tab.id;updatePrivacyWindow()
        refreshScripts(listOf(tab),applyToPage=false)
        if(url.isNotEmpty()){tab.pendingUrl=url;web.loadUrl(url)}
        persistTabs();return tab
    }
    fun exitFullscreen(){val callback=fullScreenCallback;fullScreenCallback=null;fullScreenView=null;callback?.onCustomViewHidden()}
    fun navigate(input:String,fromFavorite:Favorite?=null) {
        val url=Domains.address(input);if(url.isEmpty())return
        active?.restoringNavigation=false
        active?.externalGesture?.reset()
        recordSearchFor(active,input,url);revision++
        stopEye();sheet="";active?.error="";active?.favoriteId=fromFavorite?.id;active?.favoriteRestore=fromFavorite;active?.favoriteRestoreTouchSequence=active?.web?.touchSequence?:0L;active?.pendingUrl=url;active?.web?.loadUrl(url)
    }
    internal fun recordSearchFor(tab:BrowserTab?,input:String,url:String){if(tab?.incognito!=true)store.recordSearch(input,url)}
    /** Home is a navigation in the current tab, never a new tab or a change of profile. */
    fun openHome(){
        if(!home.enabled)return
        val tab=active?:return
        tab.restoringNavigation=false
        tab.externalGesture.reset()
        val destination=home.destination()
        stopEye();sheet="";tab.error="";tab.favoriteId=null;tab.favoriteRestore=null
        tab.web.stopLoading()
        tab.pendingUrl=destination?:"about:blank"
        tab.web.loadUrl(destination?:"about:blank")
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
    fun setCertificateException(origin:String,enabled:Boolean){
        store.setCertificateException(origin,enabled)
        tabs.forEach{it.web.clearSslPreferences()}
        revision++;sheet="";reload()
    }
    fun reload(){
        stopEye();active?.let{tab->
            tab.restoringNavigation=false
            tab.externalGesture.reset()
            val retry=tab.error.isNotEmpty()&&tab.url.isNotEmpty()
            tab.error=""
            if(retry){tab.pendingUrl=tab.url;tab.web.loadUrl(tab.url)}else tab.web.reload()
        }
    }
    fun switchTab(id:Int){val tab=tabs.find{it.id==id}?:return;capturePreview(active);stopEye();tab.lastActiveAt=System.currentTimeMillis();activeId=id;persistTabs();sheet="";updatePrivacyWindow()}
    fun closeTab(id:Int){
        if(id==activeId)stopEye()
        val tab=tabs.find{it.id==id}?:return
        previews.remove(tab.previewKey)
        tab.preview=null;tab.documentScript?.remove();tab.web.stopLoading()
        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)
        if(tab.incognito&&tabs.none{it.incognito})privateSession.clear()
        if(activeId==id)activeId=(tabs.lastOrNull{it.incognito==tab.incognito}?:tabs.lastOrNull())?.id?:0
        if(tabs.isEmpty())newTab(incognito=false)
        persistTabs();updatePrivacyWindow()
    }
    fun closePrivateTabs(){tabs.filter{it.incognito}.map{it.id}.forEach{closeTab(it)}}
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
        if(wasSelecting && applyToPage)active?.web?.evaluateJavascript("window.__chengjingEye?.enable(false);window.__chengjingEye?.configure(${config(active?.url.orEmpty())})",null)
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
    suspend fun validateCode(value:SiteRules){
        val source=JSONArray(listOf(value.js)+value.edits.map{it.js}).toString()
        val result=js("(()=>{try{for(const code of $source){new Function('element',code);}return {valid:true};}catch(e){return {valid:false,error:String(e)};}})()")
        val parsed=JSONObject(result);check(parsed.optBoolean("valid")){"JavaScript 語法有誤：${parsed.optString("error")}"}
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
    fun saveDraft(){val d=draft?:return;saveSite(d,reload=true);stopEye();notice="已儲存，將套用 ${d.domain} 及其子網域"}
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
    fun destroy(){if(destroyed)return;destroyed=true;browsingDataCleaner.close();exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null);mainHandler.removeCallbacksAndMessages(null);tabs.forEach{it.preview=null;it.documentScript?.remove();it.web.stopLoading();(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear();privateSession.clear()}
}
