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
    internal var previewCommitted=false
    internal var navigationGeneration=0L
    internal var previewGeneration=0L
    internal var lastActiveAt=System.currentTimeMillis()
    internal var restoringNavigation=false
    internal var suppressHistoryUntilNavigation=false
    internal val externalGesture=ExternalLinkGesture{android.os.SystemClock.elapsedRealtime()}
    internal var externalOpenerId:Int?=null
    internal var openedExternally=false
    var preview by mutableStateOf<Bitmap?>(null)
    internal var imageContent by mutableStateOf<ImageAsset?>(null)
    val warnings=if(incognito)CertificateWarnings()else CertificateWarnings.session
    val refreshContainer = RefreshWebContainer(web.context, web)
    var url by mutableStateOf("")
    var title by mutableStateOf(bt(R.string.msg_052052ba842e))
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
    internal var developerRevision:suspend (String,String,SiteRules,String?)->DeveloperProposal = {problem,source,current,editId->if(store.aiProvider=="gemma")gemma.develop(problem,source,current,null,true,editId)else OpenRouter().develop(store.readKey(),store.model,problem,source,current,null,true,editId)}
    internal var ruleEdit by mutableStateOf<RuleEditSession?>(null)
    val gemma=GemmaLocal(context.applicationContext)
    val icons=SiteIcons(context.applicationContext)
    val favorites=FavoriteStore(context)
    internal val home=HomePreferences(context,existingUser=store.tabs().isNotEmpty()||store.history().isNotEmpty())
    internal val homeQuotes:List<String> get()=AppLanguages.resources()?.getStringArray(R.array.home_quotes)?.toList()?:context.assets.open("home-quotes.txt").bufferedReader(Charsets.UTF_8).use{it.readLines()}.filter{it.isNotBlank()}
    internal val previews=TabPreviewStore(context)
    internal val browsingDataCleaner=BrowsingDataCleaner(this)
    private var restoringTabs=false
    private var destroyed=false
    val tabs = mutableStateListOf<BrowserTab>()
    internal val findInPage=FindInPage(this)
    private var currentTabId by mutableIntStateOf(0)
    var activeId:Int
        get()=currentTabId
        set(value){if(value!=currentTabId)findInPage.close();currentTabId=value}
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
            tab.url.isEmpty()||tab.id!=activeId||tab.web.selecting||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return
        runCatching{
            val bitmap=TabPreviewImage.capture(tab.web)?:return
            tab.previewGeneration++
            tab.preview=bitmap
            previews.save(tab.previewKey,bitmap){if(!destroyed)notice=bt(R.string.msg_778dda35c10f)}
        }
    }
    private fun requestPreviewFrame(tab:BrowserTab){
        if(destroyed||tab.incognito||tab !in tabs||tab.url.isEmpty()||!tab.previewCommitted||tab.error.isNotEmpty()||
            tab.id!=activeId||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return
        val generation=tab.navigationGeneration
        tab.web.postVisualStateCallback(generation,object:WebView.VisualStateCallback(){
            override fun onComplete(requestId:Long){
                if(!destroyed&&tab in tabs&&generation==tab.navigationGeneration&&tab.previewCommitted&&tab.error.isEmpty()){
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
        if(!previews.flush())notice=bt(R.string.msg_243ab3a6c770)
    }
    internal fun cookiesFor(tab:BrowserTab)=if(tab.incognito)privateSession.cookies(tab.web)else CookieManager.getInstance()
    internal fun downloadFor(tab:BrowserTab,url:String,page:String,agent:String,mime:String?=null,disposition:String?=null,imageOnly:Boolean=true,confirmed:Boolean=false){
        if(tab !in tabs)return
        if(imageOnly){(context as? MainActivity)?.imageActions?.perform(tab,PageImageSource(url,page,tab.title),ImageAction.DOWNLOAD);return}
        val generation=tab.navigationGeneration
        val action={
            if(tab in tabs&&tab.navigationGeneration==generation){
                if(DownloadFormat.inline(url))(context as? MainActivity)?.pageDownloads?.download(tab,url,mime,disposition)
                else (context as? MainActivity)?.imageDownloads?.download(url,page,agent,
                    cookieHeader=cookiesFor(tab).getCookie(url),mimeHint=mime,disposition=disposition,imageOnly=false)
            }
            Unit
        }
        if(tab.incognito&&!confirmed)prompts.confirm(bt(R.string.msg_c64cba804f4c),bt(R.string.msg_569815e6ceb4),bt(R.string.msg_d477c75aa656),
            owner=tab.id,valid={tab in tabs&&tab.id==activeId},confirm=action)
        else action()
    }
    val active: BrowserTab? get() = tabs.find { it.id == activeId }
    var revision by mutableIntStateOf(0)
    var eye by mutableStateOf(false)
    var selection by mutableStateOf<Selection?>(null)
    var draft by mutableStateOf<SiteRules?>(null)
    var dirty by mutableStateOf(false)
    var notice by mutableStateOf("")
    private var sheetState by mutableStateOf("")
    var sheet:String
        get()=sheetState
        set(value){if(value.isNotEmpty())findInPage.close();sheetState=value}
    internal val prompts=BrowserPrompts()
    internal var imagePreview by mutableStateOf<ImageAsset?>(null)
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
        return JSONObject().put("strings",messages()).put("sites",JSONArray(sites.map{it.json()})).put("exceptions",JSONArray(bypass)).toString()
    }
    private fun messages():JSONObject=JSONObject().put("元件規則格式不正確",bt(R.string.msg_485107965fef)).put("不能移除整個頁面",bt(R.string.msg_8ace37cfd750)).put("找不到有效的元件選擇器",bt(R.string.msg_8b9869b822e7)).put("所選元件已不存在",bt(R.string.msg_e120bd1bed88)).put("原始 HTML 尚未準備好，請重新載入頁面再開啟規則",bt(R.string.msg_db7282021ef2)).put("找不到元件",bt(R.string.msg_f3cfde65b6f6)).put("元件過大，請選擇更小範圍",bt(R.string.msg_837653b6c57e))
    fun refreshLanguage(){
        refreshScripts(applyToPage=false)
        val text=messages()
        // Updating captions must not clear an unsaved preview or reapply website edits.
        tabs.filter{it.url.isNotEmpty()}.forEach{it.web.evaluateJavascript("window.__chengjingEye?.localize($text)",null)}
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
        val normal=tabs.filterNot{it.incognito||it.imageContent!=null}
        val saved=store.saveTabs(normal.map{it.url},normal.map{it.favoriteId},normal.map{it.previewKey!!},normal.map{it.title},normal.map{it.lastActiveAt},normal.map{it.openedExternally})
        if(!saved)notice=bt(R.string.msg_49e07d37a692)
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
        if(mode=="custom")require(custom.isNotBlank()&&custom.length<=1024&&custom.all{it.code in 32..126}){bt(R.string.msg_0e1bfa10059d)}
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
    internal fun openExternalTab(url:String):BrowserTab? {
        active?.takeIf{it.canReceiveExternalLink()}?.let{tab->
            tab.openedExternally=true;tab.url=url;navigate(url);persistTabs();return tab
        }
        return createTab(url,false,null,openedExternally=true)
    }
    internal fun newImageTab(image:ImageAsset,incognito:Boolean):BrowserTab?=createTab("",incognito,null,image)
    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url:String,incognito:Boolean,restored:SavedBrowserTab?,image:ImageAsset?=null,openedExternally:Boolean=false):BrowserTab? {
        if(incognito&&!privateSession.supported){sheet="";notice=bt(R.string.msg_98603eac2c1f);return null}
        if(tabs.size>=20){notice=bt(R.string.msg_4369d178c9de);return null}
        capturePreview(active)
        (context as? MainActivity)?.websiteLocation?.pauseFor(activeId)
        stopEye()
        val web=SelectionWebView(context)
        if(incognito)try{privateSession.attach(web)}catch(_:Exception){web.destroy();notice=bt(R.string.msg_b860234cdca4);return null}
        // Discard WebView trust decisions from older versions; only saved site choices apply.
        web.clearSslPreferences()
        val tab=BrowserTab(nextId++,web,incognito,if(incognito)null else restored?.key?:TabPreviewStore.newKey())
        tab.url=url
        tab.openedExternally=restored?.openedExternally?:openedExternally
        tab.imageContent=image
        if(image!=null){tab.title=bt(R.string.msg_38e077277302 ,image.title.ifBlank{bt(R.string.msg_b210350f38fc)}.take(80));if(!incognito)tab.preview=image.thumbnail}
        restored?.let{record->
            tab.lastActiveAt=record.lastActiveAt
            tab.restoringNavigation=url.isNotEmpty()
            tab.title=record.title.ifBlank{bt(R.string.msg_052052ba842e)}
            tab.favoriteId=record.favoriteId?.let{favorites.get(it)}?.takeIf{Domains.scope(it.url)==Domains.scope(url)}?.id
            val generation=tab.previewGeneration
            previews.load(tab.previewKey){bitmap->
                if(!destroyed&&tab in tabs&&tab.preview==null&&generation==tab.previewGeneration&&bitmap!=null&&TabPreviewImage.hasContent(bitmap))tab.preview=bitmap
                else bitmap?.recycle()
            }
        }
        web.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(view:android.view.View){requestPreviewFrame(tab)}
            override fun onViewDetachedFromWindow(view:android.view.View){}
        })
        web.addOnLayoutChangeListener{_,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom->
            if(right-left!=oldRight-oldLeft||bottom-top!=oldBottom-oldTop)requestPreviewFrame(tab)
        }
        tab.refreshContainer.setOnRefreshListener {
            if (web.selecting || tab !in tabs) tab.refreshContainer.isRefreshing=false
            else { tab.error=""; web.reload() }
        }
        web.setBackgroundColor(android.graphics.Color.WHITE)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true;setGeolocationEnabled(true)
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
                        "codeError" -> { notice=bt(R.string.msg_2988ade50b61) }
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
                if(request.url.host=="practice.chengjing.invalid" && request.isForMainFrame) return WebResourceResponse("text/html","UTF-8",context.assets.open("i18n/${AppLanguages.currentTag}/practice.html"))
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
                    if(request.isForMainFrame){recordBlocked(tab,bt(R.string.msg_a782840b186d),u);if(allowed)notice=bt(R.string.msg_87f999fe4fd3)}
                    return true
                }
                if(!request.isForMainFrame)return false
                val scope=Domains.scope(tab.url)
                if(scope !in exceptions && store.get(scope).guard && !request.hasGesture() && !request.isRedirect && tab.url.isNotEmpty()) {
                    tab.blockedUrl=u;recordBlocked(tab,bt(R.string.msg_216baff9f183),u);return true
                }
                if(openExternalLink(tab,u,allowed))return true
                tab.pendingUrl=u
                return false
            }
            override fun onPageStarted(view:WebView,url:String,favicon:Bitmap?) {
                (context as? MainActivity)?.websiteLocation?.cancelFor(tab.id)
                if(tab !in tabs)return
                if(web.selecting){view.stopLoading();return}
                (context as? MainActivity)?.pageDownloads?.cancelFor(tab.id)
                if(!tab.restoringNavigation)tab.lastActiveAt=System.currentTimeMillis()
                tab.suppressHistoryUntilNavigation=false
                prompts.closeFor(tab.id)
                findInPage.closeFor(tab.id)
                tab.navigationGeneration++
                tab.previewReady=false
                tab.previewCommitted=false
                // Keep the last successful thumbnail during reload, offline restore and errors.
                tab.url=if(url=="about:blank")""else url
                tab.pendingUrl=url;tab.error="";tab.blockedUrl=""
                if(url=="about:blank"){
                    tab.title=bt(R.string.msg_01e143a4ff67);tab.previewGeneration++;tab.preview=null
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
                    tab.updateNavigationState();persistTabs()
                }
            }
            override fun onPageCommitVisible(view:WebView,url:String){
                if(tab in tabs&&tab.url==url&&tab.error.isEmpty()){
                    tab.previewCommitted=true
                    requestPreviewFrame(tab)
                }
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(tab !in tabs)return
                tab.externalGesture.reset()
                tab.externalOpenerId=null
                tab.refreshContainer.isRefreshing=false
                tab.updateNavigationState()
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
                findInPage.pageFinished(tab)
                if(tab.url==url)tab.previewCommitted=true
                requestPreviewFrame(tab)
                // Fallback remains usable on older WebView; document-start protection requires an update.
                if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))view.evaluateJavascript(script.replace("__CJ_CONFIG__",config(tab.url)),null)
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(web.selecting)return
                if(request.isForMainFrame){findInPage.closeFor(tab.id);tab.refreshContainer.isRefreshing=false;tab.error=if(error.errorCode==ERROR_FAILED_SSL_HANDSHAKE)bt(R.string.msg_7fa9669b644d)else bt(R.string.msg_91fdf6f9c33c);tab.progress=100}
            }
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError){
                val reason=when(error.primaryError){
                    android.net.http.SslError.SSL_EXPIRED->bt(R.string.msg_5e2122abec02)
                    android.net.http.SslError.SSL_NOTYETVALID->bt(R.string.msg_e7820bdc25e3)
                    android.net.http.SslError.SSL_IDMISMATCH->bt(R.string.msg_231cbdd2b560)
                    android.net.http.SslError.SSL_UNTRUSTED->bt(R.string.msg_158f94fc2a3c)
                    android.net.http.SslError.SSL_DATE_INVALID->bt(R.string.msg_557874fe9910)
                    else->bt(R.string.msg_e6ed408e0f8a)
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
                        tab.error=bt(R.string.msg_3ca921ad80a6);tab.progress=100;tab.refreshContainer.isRefreshing=false
                    }
                }
            }
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {
                (context as? MainActivity)?.websiteLocation?.cancelFor(tab.id)
                findInPage.closeFor(tab.id)
                tab.refreshContainer.isRefreshing=false
                tab.error=bt(R.string.msg_31648c936ef3);return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            private fun websitePrompt(url:String,message:String,result:android.webkit.JsResult,initial:String?=null,beforeUnload:Boolean=false,alert:Boolean=false):Boolean {
                if(tab !in tabs||tab.id!=activeId||web.selecting){result.cancel();return true}
                val generation=tab.navigationGeneration
                val site=android.net.Uri.parse(url).host?:bt(R.string.msg_8b044a42e0e2)
                prompts.show(BrowserPrompt.Confirm(
                    if(beforeUnload)bt(R.string.msg_e0eb3e5057cc)else bt(R.string.msg_3bf1281815df ,site),message,
                    if(beforeUnload)bt(R.string.msg_70fbb6a5dbfa)else bt(R.string.msg_20db9f87b860),{result.confirm()},if(beforeUnload)bt(R.string.msg_a2cb5003004e)else bt(R.string.msg_2cd0f3be8738),
                    tab.id,{tab in tabs&&tab.id==activeId&&tab.navigationGeneration==generation},{result.cancel()},
                    input=initial,submit=if(result is android.webkit.JsPromptResult)({value:String->result.confirm(value)})else null,
                    showCancel=!alert
                ));return true
            }
            override fun onJsAlert(view:WebView,url:String,message:String,result:android.webkit.JsResult)=websitePrompt(url,message,result,alert=true)
            override fun onJsConfirm(view:WebView,url:String,message:String,result:android.webkit.JsResult)=websitePrompt(url,message,result)
            override fun onJsPrompt(view:WebView,url:String,message:String,defaultValue:String?,result:android.webkit.JsPromptResult)=websitePrompt(url,message,result,initial=defaultValue.orEmpty())
            override fun onJsBeforeUnload(view:WebView,url:String,message:String,result:android.webkit.JsResult)=websitePrompt(url,message,result,beforeUnload=true)
            override fun onProgressChanged(view:WebView,value:Int){tab.progress=value}
            override fun onReceivedIcon(view:WebView,icon:Bitmap?){if(icon!=null&&!tab.incognito)view.url?.let{icons.remember(it,icon)}}
            override fun onReceivedTitle(view:WebView,title:String?){
                tab.title=if(tab.url.isEmpty()&&view.url=="about:blank")bt(R.string.msg_01e143a4ff67)else title?.take(180)?:tab.url
                persistTabs()
            }
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(web.selecting) return false
                val scope=Domains.scope(tab.url)
                if(!isUserGesture || (scope !in exceptions && store.get(scope).guard)) {recordBlocked(tab,bt(R.string.msg_e9ea75903633));return false}
                val child=newTab(incognito=tab.incognito) ?: return false
                child.externalGesture.popupFromClick();child.externalOpenerId=tab.id
                (resultMsg.obj as WebView.WebViewTransport).webView=child.web;resultMsg.sendToTarget();return true
            }
            override fun onPermissionRequest(request:PermissionRequest){request.deny();notice=bt(R.string.msg_2138c825b189)}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){
                val location=(context as? MainActivity)?.websiteLocation
                if(location!=null)location.request(tab,origin,callback)else callback.invoke(origin,false,false)
            }
            override fun onGeolocationPermissionsHidePrompt(){(context as? MainActivity)?.websiteLocation?.hideLegacyPrompt(tab.id)}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                if(web.selecting){callback.onReceiveValue(null);return true}
                fileCallback?.onReceiveValue(null);fileCallback=callback
                runCatching { chooseFiles?.invoke(params.createIntent()) ?: error(bt(R.string.msg_3fe646f9b847)) }.onFailure {fileCallback?.onReceiveValue(null);fileCallback=null;notice=bt(R.string.msg_beffea44da0c)};return true
            }
            override fun onShowCustomView(view:android.view.View,callback:CustomViewCallback){if(fullScreenView!=null){callback.onCustomViewHidden();return};stopEye();sheet="";(context as? android.app.Activity)?.let{activity->activity.currentFocus?.clearFocus();(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)};fullScreenView=view;fullScreenCallback=callback}
            override fun onHideCustomView(){exitFullscreen()}
        }
        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if(web.selecting)return@setDownloadListener
            if(!DownloadFormat.supported(url)){notice=bt(R.string.msg_ad37ef430462);return@setDownloadListener}
            val inline=DownloadFormat.inline(url)
            val name=if(inline)DownloadFormat.filename(null,DownloadFormat.mime(mime).orEmpty())else URLUtil.guessFileName(url,disposition,mime)
            val generation=tab.navigationGeneration;val page=tab.url
            val message=name+(if(inline)bt(R.string.msg_f6082c7dc342)else"")+(if(tab.incognito)bt(R.string.msg_29eec08993b2)else"")
            prompts.confirm(bt(R.string.msg_d71c2bea9463),message,bt(R.string.msg_d477c75aa656),owner=tab.id,valid={tab in tabs&&tab.id==activeId&&tab.navigationGeneration==generation}) {
                downloadFor(tab,url,page,userAgent,mime,disposition,imageOnly=false,confirmed=true)
            }
        }
        PageContextMenu(this,tab).install()
        tabs.add(tab);activeId=tab.id;updatePrivacyWindow()
        (context as? MainActivity)?.websiteLocation?.attach(tab)
        refreshScripts(listOf(tab),applyToPage=false)
        if(url.isNotEmpty()){tab.pendingUrl=url;web.loadUrl(url)}
        persistTabs();return tab
    }
    fun exitFullscreen(){val callback=fullScreenCallback;fullScreenCallback=null;fullScreenView=null;callback?.onCustomViewHidden()}
    fun navigate(input:String,fromFavorite:Favorite?=null) {
        val resolution=Domains.resolve(input,store.searchSettings);val url=resolution.url;if(url.isEmpty())return
        active?.imageContent=null
        active?.restoringNavigation=false
        active?.externalGesture?.reset()
        recordSearchFor(active,input,url,resolution.search);revision++
        stopEye();sheet="";active?.error="";active?.favoriteId=fromFavorite?.id;active?.favoriteRestore=fromFavorite;active?.favoriteRestoreTouchSequence=active?.web?.touchSequence?:0L;active?.pendingUrl=url;active?.web?.loadUrl(url)
    }
    internal fun recordSearchFor(tab:BrowserTab?,input:String,url:String,isSearch:Boolean=SearchEngines.looksLikeSearch(url)){if(tab?.incognito!=true)store.recordSearch(input,url,isSearch=isSearch)}
    /** Home is a navigation in the current tab, never a new tab or a change of profile. */
    fun openHome(){
        if(!home.enabled)return
        val tab=active?:return
        tab.imageContent=null
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
        if(domain.isEmpty()){notice=bt(R.string.msg_471bd5d0a10b);return null}
        val expected=tab.web.url
        val raw=js("JSON.stringify({url:location.href,title:document.title,y:scrollY,progress:scrollY/Math.max(1,document.documentElement.scrollHeight-innerHeight)})")
        if(activeId!=tab.id || tab.web.url!=expected){notice=bt(R.string.msg_6846a5d84ad9);return null}
        return runCatching{
            val data=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
            val favorite=favorites.save(data.getString("url"),data.optString("title",tab.title),data.optDouble("y",0.0),data.optDouble("progress",0.0),tab.favoriteId,forceNew)
            tab.favoriteId=favorite.id;revision++;persistTabs();favorite
        }.onFailure{notice=bt(R.string.msg_c0af0b409136 ,it.localizedMessage)}.getOrNull()
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
    fun switchTab(id:Int){val tab=tabs.find{it.id==id}?:return;capturePreview(active);(context as? MainActivity)?.websiteLocation?.pauseFor(activeId);stopEye();tab.lastActiveAt=System.currentTimeMillis();activeId=id;(context as? MainActivity)?.websiteLocation?.resumeFor(id);persistTabs();sheet="";updatePrivacyWindow()}
    fun closeTab(id:Int,replaceLast:Boolean=true){
        (context as? MainActivity)?.websiteLocation?.detach(id)
        findInPage.closeFor(id)
        (context as? MainActivity)?.pageDownloads?.cancelFor(id)
        prompts.closeFor(id)
        if(id==activeId)stopEye()
        val tab=tabs.find{it.id==id}?:return
        previews.remove(tab.previewKey)
        tab.preview=null;tab.documentScript?.remove();tab.web.stopLoading()
        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)
        if(tab.incognito&&tabs.none{it.incognito}){privateSession.clear();(context as? MainActivity)?.imageActions?.clearPrivate();(context as? MainActivity)?.websiteLocation?.clearPrivate()}
        if(activeId==id)activeId=(tabs.lastOrNull{it.incognito==tab.incognito}?:tabs.lastOrNull())?.id?:0
        (context as? MainActivity)?.websiteLocation?.resumeFor(activeId)
        if(tabs.isEmpty()&&replaceLast)newTab(incognito=false)
        persistTabs();updatePrivacyWindow()
    }
    fun closePrivateTabs(){tabs.filter{it.incognito}.map{it.id}.forEach{closeTab(it)}}
    fun beginEye(){
        if(domain.isEmpty()){notice=bt(R.string.msg_6174d4e4a019);return}
        if(isException){notice=bt(R.string.msg_3aa17e8a8d60);return}
        if(!isSupported){notice=bt(R.string.msg_91dcb6b2e4a7);return}
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
            val j=runCatching{JSONObject(result)}.getOrNull()?:return bt(R.string.msg_80f5b5b18703)
            if(!j.optBoolean("valid"))return j.optString("error",bt(R.string.msg_4bf5b0db379a))
            if(j.optInt("count")==0)return bt(R.string.msg_67eb218e5f40 ,s)
        }
        return null
    }
    suspend fun validateCode(value:SiteRules){
        val source=JSONArray(listOf(value.js)+value.edits.map{it.js}).toString()
        val result=js("(()=>{try{for(const code of $source){new Function('element',code);}return {valid:true};}catch(e){return {valid:false,error:String(e)};}})()")
        val parsed=JSONObject(result);check(parsed.optBoolean("valid")){bt(R.string.msg_a9392bcaf61d ,parsed.optString("error"))}
    }
    fun previewSite(value:SiteRules){draft=value;dirty=true;active?.web?.evaluateJavascript("window.__chengjingEye?.preview(${value.json()})",null)}
    suspend fun removeSelected():Boolean {
        val s=selection?:return false
        validateSelectors(listOf(s.selector))?.let{notice=it;return false}
        val value=draft?:site
        previewSite(value.copy(rules=(value.rules+ElementRule(s.selector,s.label)).distinctBy{it.selector}))
        selection=null;sheet="";notice=bt(R.string.msg_e60ebf394403);return true
    }
    fun undoDraft(){draft?.let{previewSite(it.copy(rules=it.rules.dropLast(1)))};selection=null;sheet=""}
    fun saveDraft(){val d=draft?:return;saveSite(d,reload=true);stopEye();notice=bt(R.string.msg_dc7f07415b2e ,d.domain)}
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
        notice=if(d in exceptions)bt(R.string.msg_edeb1a15813d)else bt(R.string.msg_a4038cb71105)
    }
    fun restoreRules(domain:String){if(store.undo(domain)){reloadSite(domain);notice=bt(R.string.msg_b3f41aaeb32b)}}
    fun destroy(){if(destroyed)return;findInPage.close();destroyed=true;prompts.cancel();browsingDataCleaner.close();exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null);mainHandler.removeCallbacksAndMessages(null);tabs.forEach{it.preview=null;it.documentScript?.remove();it.web.stopLoading();(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear();privateSession.clear()}
}
