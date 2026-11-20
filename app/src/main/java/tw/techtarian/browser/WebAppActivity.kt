package tw.techtarian.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import org.json.JSONArray
import org.json.JSONObject

class WebAppActivity : AppCompatActivity() {
    private lateinit var store: BrowserStore
    private lateinit var webApps: WebAppStore
    private lateinit var downloads: BrowserImageDownloads
    private lateinit var web: SelectionWebView
    private lateinit var refreshContainer: RefreshWebContainer
    private var documentScript: ScriptHandler? = null
    private val warnings = CertificateWarnings()
    private val externalGesture = ExternalLinkGesture { android.os.SystemClock.elapsedRealtime() }

    private var appState by mutableStateOf<InstalledWebApp?>(null)
    private var currentUrl by mutableStateOf("")
    private var pageTitle by mutableStateOf("")
    private var loadProgress by mutableIntStateOf(100)
    private var canBack by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var certificateWarning by mutableStateOf("")
    private var notice by mutableStateOf("")
    private var fullScreenView by mutableStateOf<View?>(null)
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingPrompt by mutableStateOf<WebAppDialogPrompt?>(null)
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private data class WebAppDialogPrompt(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val cancelLabel: String,
        val showCancel: Boolean,
        val initialInput: String? = null,
        val onConfirm: (String) -> Unit,
        val onCancel: () -> Unit,
    )

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult
        val uris = if (result.resultCode == RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else null
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = BrowserAppearance.mode(BrowserAppearance.savedChoice(this))
        super.onCreate(savedInstanceState)
        AppLanguages.initialize(this)
        setTheme(R.style.AppTheme)
        enableEdgeToEdge()

        store = BrowserStore(this)
        webApps = WebAppStore(this)
        downloads = BrowserImageDownloads(this) { msg -> notice = msg }

        val target = resolveWebApp(intent)
        if (target == null) {
            Toast.makeText(this, bt(R.string.webapp_notice_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (target.mode == WebAppDisplayMode.SHORTCUT) {
            openInMainBrowser(target.url)
            finish()
            return
        }

        appState = target
        currentUrl = target.url
        pageTitle = target.name
        applyTaskDescription(target)
        setupWebView(target)

        setContent {
            WebAppScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = resolveWebApp(intent) ?: return
        if (target.mode == WebAppDisplayMode.SHORTCUT) {
            openInMainBrowser(target.url)
            finish()
            return
        }
        val changed = appState?.id != target.id
        appState = target
        applyTaskDescription(target)
        if (changed && ::web.isInitialized) {
            currentUrl = target.url
            pageTitle = target.name
            errorMessage = ""
            web.loadUrl(target.url)
        }
    }

    private fun resolveWebApp(incoming: Intent?): InstalledWebApp? {
        if (incoming == null) return null
        val idFromExtra = incoming.getStringExtra(WebAppLauncher.EXTRA_WEB_APP_ID)?.takeIf(WebAppPolicy::validId)
        val idFromUri = WebAppPolicy.parseShortcutId(incoming.dataString)
        val id = idFromExtra ?: idFromUri ?: return null
        return webApps.get(id)
    }

    @Suppress("DEPRECATION")
    private fun applyTaskDescription(app: InstalledWebApp) {
        val icon = webApps.loadIcon(app)
        val taskDesc = if (Build.VERSION.SDK_INT >= 33) {
            val builder = ActivityManager.TaskDescription.Builder().setLabel(app.name)
            if (icon == null) builder.setIcon(R.mipmap.ic_launcher)
            app.themeColor?.let { builder.setPrimaryColor(it) }
            builder.build()
        } else {
            val bitmap = icon?.let { WebAppStore.renderAdaptiveBitmap(it, app.name, app.url, app.themeColor) }
            if (bitmap != null) {
                ActivityManager.TaskDescription(app.name, bitmap, app.themeColor ?: 0xFF147A64.toInt())
            } else {
                ActivityManager.TaskDescription(app.name, R.mipmap.ic_launcher)
            }
        }
        setTaskDescription(taskDesc)
    }

    private fun skyEyeConfig(): String = JSONObject()
        .put("strings", skyEyeMessages())
        .put("sites", JSONArray(store.all().map { it.json() }))
        .put("exceptions", JSONArray())
        .toString()

    private fun resolveUserAgent(): String {
        val original = WebSettings.getDefaultUserAgent(this)
        return when {
            store.userAgentMode == "webview" -> original
            store.userAgentMode == "custom" && store.customUserAgent.isNotBlank() -> store.customUserAgent
            else -> BrowserUserAgent.mobile(original)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(app: InstalledWebApp) {
        web = SelectionWebView(this)
        refreshContainer = RefreshWebContainer(this, web)
        refreshContainer.setOnRefreshListener {
            errorMessage = ""
            web.reload()
        }
        web.setBackgroundColor(android.graphics.Color.WHITE)
        web.clearSslPreferences()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            setGeolocationEnabled(true)
            userAgentString = resolveUserAgent()
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = true
        }
        SystemAutofill.configure(web, private = false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        val skyEyeSource = runCatching { assets.open("skyeye.js").bufferedReader().readText() }.getOrDefault("")
        if (skyEyeSource.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentScript = WebViewCompat.addDocumentStartJavaScript(
                web,
                skyEyeSource.replace("__CJ_CONFIG__", skyEyeConfig()),
                setOf("*"),
            )
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val targetUrl = request.url.toString()
                if (request.isForMainFrame && targetUrl == "about:blank") return false
                val allowed = externalGesture.allow(request.isForMainFrame, request.hasGesture(), request.isRedirect)
                if (!ExternalLinkPolicy.isWeb(request.url.scheme)) {
                    if (allowed && handleExternalScheme(targetUrl)) return true
                    if (request.isForMainFrame && allowed) notice = bt(R.string.msg_87f999fe4fd3)
                    return true
                }
                if (!request.isForMainFrame) return false
                val scope = Domains.scope(currentUrl)
                if (store.get(scope).guard && !request.hasGesture() && !request.isRedirect && currentUrl.isNotEmpty()) {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                web.resetDomScrollState()
                currentUrl = url
                errorMessage = ""
                certificateWarning = warnings.messageFor(url)
                canBack = view.canGoBack()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    currentUrl = url
                    canBack = view.canGoBack()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                externalGesture.reset()
                refreshContainer.isRefreshing = false
                currentUrl = url
                canBack = view.canGoBack()
                certificateWarning = warnings.messageFor(url)
                if (skyEyeSource.isNotEmpty() && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view.evaluateJavascript(skyEyeSource.replace("__CJ_CONFIG__", skyEyeConfig()), null)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    refreshContainer.isRefreshing = false
                    errorMessage = if (error.errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                        bt(R.string.msg_7fa9669b644d)
                    } else {
                        bt(R.string.msg_91fdf6f9c33c)
                    }
                    loadProgress = 100
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val reason = when (error.primaryError) {
                    SslError.SSL_EXPIRED -> bt(R.string.msg_5e2122abec02)
                    SslError.SSL_NOTYETVALID -> bt(R.string.msg_e7820bdc25e3)
                    SslError.SSL_IDMISMATCH -> bt(R.string.msg_231cbdd2b560)
                    SslError.SSL_UNTRUSTED -> bt(R.string.msg_158f94fc2a3c)
                    SslError.SSL_DATE_INVALID -> bt(R.string.msg_557874fe9910)
                    else -> bt(R.string.msg_e6ed408e0f8a)
                }
                val page = currentUrl.ifBlank { view.url.orEmpty() }
                warnings.record(page, error.url, reason)
                certificateWarning = warnings.messageFor(page.ifBlank { error.url })
                if (store.certificateException(error.url)) {
                    handler.proceed()
                } else {
                    handler.cancel()
                    if (CertificateExceptions.sameDocument(page, error.url)) {
                        errorMessage = bt(R.string.msg_3ca921ad80a6)
                        loadProgress = 100
                        refreshContainer.isRefreshing = false
                    }
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                refreshContainer.isRefreshing = false
                errorMessage = bt(R.string.msg_31648c936ef3)
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadProgress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                val clean = title?.trim().orEmpty()
                if (clean.isNotEmpty()) pageTitle = clean.take(180)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false
                val hit = view.hitTestResult
                val resolved = PageActionPolicy.resolve(hit.extra, currentUrl)
                    ?.takeIf { ExternalLinkPolicy.isWeb(Uri.parse(it).scheme) }
                if (resolved != null) {
                    view.loadUrl(resolved)
                }
                return false
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                val chooserIntent = runCatching { fileChooserParams.createIntent() }.getOrNull()
                if (chooserIntent == null) {
                    pendingFileCallback = null
                    filePathCallback.onReceiveValue(null)
                    return true
                }
                runCatching { fileChooser.launch(chooserIntent) }.onFailure {
                    pendingFileCallback = null
                    filePathCallback.onReceiveValue(null)
                    notice = bt(R.string.web_capture_error)
                }
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullScreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullScreenView = view
                fullScreenCallback = callback
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showJsPrompt(url, message, result, alert = true)
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showJsPrompt(url, message, result)
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String,
                message: String,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                showJsPrompt(url, message, result, initial = defaultValue.orEmpty())
                return true
            }

            override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
                showJsPrompt(url, message, result, beforeUnload = true)
                return true
            }
        }

        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if (!DownloadFormat.supported(url) || DownloadFormat.inline(url)) {
                notice = bt(R.string.msg_ad37ef430462)
                return@setDownloadListener
            }
            val filename = URLUtil.guessFileName(url, disposition, mime)
            pendingPrompt = WebAppDialogPrompt(
                title = bt(R.string.msg_d71c2bea9463),
                message = filename,
                confirmLabel = bt(R.string.msg_d477c75aa656),
                cancelLabel = bt(R.string.msg_2cd0f3be8738),
                showCancel = true,
                onConfirm = {
                    downloads.download(
                        rawUrl = url,
                        page = currentUrl,
                        userAgent = userAgent,
                        cookieHeader = CookieManager.getInstance().getCookie(url),
                        mimeHint = mime,
                        disposition = disposition,
                        imageOnly = false,
                    )
                },
                onCancel = {},
            )
        }

        web.loadUrl(app.url)
    }

    private fun showJsPrompt(
        url: String,
        message: String,
        result: JsResult,
        initial: String? = null,
        beforeUnload: Boolean = false,
        alert: Boolean = false,
    ) {
        val site = Uri.parse(url).host ?: bt(R.string.msg_8b044a42e0e2)
        pendingPrompt?.onCancel?.invoke()
        pendingPrompt = WebAppDialogPrompt(
            title = if (beforeUnload) bt(R.string.msg_e0eb3e5057cc) else bt(R.string.msg_3bf1281815df, site),
            message = message,
            confirmLabel = if (beforeUnload) bt(R.string.msg_70fbb6a5dbfa) else bt(R.string.msg_20db9f87b860),
            cancelLabel = if (beforeUnload) bt(R.string.msg_a2cb5003004e) else bt(R.string.msg_2cd0f3be8738),
            showCancel = !alert,
            initialInput = initial,
            onConfirm = { input ->
                if (result is JsPromptResult) result.confirm(input) else result.confirm()
            },
            onCancel = { result.cancel() },
        )
    }

    private fun handleExternalScheme(rawUrl: String): Boolean {
        val link = ExternalLinks.parse(rawUrl) ?: return false
        if (ExternalLinks.launch(this, link)) return true
        link.fallback?.let { web.loadUrl(it); return true }
        return false
    }

    private fun openInMainBrowser(url: String) {
        val safeUrl = PageActionPolicy.shareUrl(url) ?: appState?.url ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
    }

    private fun shareCurrentPage() {
        val chooser = PageSharing.chooser(currentUrl, pageTitle)
        if (chooser == null) {
            notice = bt(R.string.msg_1cba5bfa6f7a)
            return
        }
        runCatching { startActivity(chooser) }.onFailure { notice = bt(R.string.msg_9e2276893fae) }
    }

    private fun copyCurrentUrl() {
        val safeUrl = PageActionPolicy.shareUrl(currentUrl) ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("url", safeUrl))
        notice = bt(R.string.webapp_notice_copied)
    }

    private fun exitFullscreen() {
        val cb = fullScreenCallback
        fullScreenCallback = null
        fullScreenView = null
        cb?.onCustomViewHidden()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguages.refresh(this)
        if (::web.isInitialized) web.dispatchConfigurationChanged(newConfig)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pendingPrompt?.onCancel?.invoke()
        pendingPrompt = null
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        exitFullscreen()
        documentScript?.remove()
        if (::web.isInitialized) {
            web.stopLoading()
            if (::refreshContainer.isInitialized) refreshContainer.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    @Composable
    private fun WebAppScreen() {
        val app = appState ?: return
        val dark = isSystemInDarkTheme()
        val cs = browserColorScheme()
        val snackbar = remember { SnackbarHostState() }
        val inScope = remember(app, currentUrl) { WebAppPolicy.isInScope(app, currentUrl) }
        val hasSecurityWarning = certificateWarning.isNotEmpty() || store.certificateException(currentUrl)
        val showTopBar = app.mode == WebAppDisplayMode.MINIMAL_UI || !inScope || hasSecurityWarning || errorMessage.isNotEmpty()
        var menuOpen by remember { mutableStateOf(false) }

        SideEffect {
            browserSystemBars(window, window.decorView, !dark && fullScreenView == null, fullScreenView != null)
        }
        LaunchedEffect(this) {
            snapshotFlow { notice }.filter { it.isNotEmpty() }.collectLatest { message ->
                notice = ""
                snackbar.showSnackbar(message)
            }
        }
        BackHandler {
            when {
                pendingPrompt != null -> {
                    val p = pendingPrompt
                    pendingPrompt = null
                    p?.onCancel?.invoke()
                }
                fullScreenView != null -> exitFullscreen()
                web.canGoBack() -> web.goBack()
                else -> finish()
            }
        }

        MaterialTheme(colorScheme = cs, typography = BrowserTypography) {
            Surface(Modifier.fillMaxSize(), color = cs.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    Column(Modifier.fillMaxSize()) {
                        if (showTopBar) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().testTag("webapp-topbar"),
                                color = cs.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (canBack) {
                                        IconButton(onClick = { web.goBack() }, modifier = Modifier.size(44.dp)) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, bt(R.string.msg_13256df2ec81), Modifier.size(20.dp))
                                        }
                                    } else {
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Icon(
                                        imageVector = if (hasSecurityWarning) Icons.Outlined.WarningAmber
                                        else if (currentUrl.startsWith("https://")) Icons.Outlined.Lock
                                        else Icons.Outlined.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (hasSecurityWarning) cs.error else cs.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = if (!inScope) bt(R.string.webapp_external_scope_hint) else pageTitle.ifBlank { app.name },
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = WebAppPolicy.hostFor(currentUrl).ifBlank { app.scope },
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            color = if (hasSecurityWarning) cs.error else cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(textDirection = TextDirection.Ltr),
                                        )
                                    }
                                    if (!inScope) {
                                        IconButton(
                                            onClick = { errorMessage = ""; web.loadUrl(app.url) },
                                            modifier = Modifier.size(44.dp).testTag("webapp-return-home"),
                                        ) {
                                            Icon(Icons.Outlined.Home, bt(R.string.webapp_action_home), Modifier.size(20.dp))
                                        }
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { menuOpen = true },
                                            modifier = Modifier.size(44.dp).testTag("webapp-menu-button"),
                                        ) {
                                            Icon(Icons.Outlined.MoreVert, bt(R.string.webapp_action_more), Modifier.size(20.dp))
                                        }
                                        DropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false },
                                            containerColor = cs.surfaceContainerHighest,
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                                        ) {
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.msg_5f451c71b46a)) },
                                                onClick = { menuOpen = false; errorMessage = ""; web.reload() },
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Home, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.webapp_action_home)) },
                                                onClick = { menuOpen = false; errorMessage = ""; web.loadUrl(app.url) },
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.webapp_action_copy_url)) },
                                                onClick = { menuOpen = false; copyCurrentUrl() },
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.msg_7e564575eb7d)) },
                                                onClick = { menuOpen = false; shareCurrentPage() },
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.webapp_action_open_browser)) },
                                                onClick = { menuOpen = false; openInMainBrowser(currentUrl) },
                                            )
                                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(20.dp)) },
                                                text = { Text(bt(R.string.webapp_action_close)) },
                                                onClick = { menuOpen = false; finish() },
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))
                        }
                        if (loadProgress < 100) {
                            LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                trackColor = Color.Transparent,
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (errorMessage.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = cs.primary)
                                    Spacer(Modifier.height(20.dp))
                                    Text(bt(R.string.msg_60db4174fe1b), style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(10.dp))
                                    Text(errorMessage, color = cs.onSurfaceVariant)
                                    Spacer(Modifier.height(20.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(onClick = { openInMainBrowser(currentUrl) }) {
                                            Text(bt(R.string.webapp_action_open_browser))
                                        }
                                        Button(onClick = { errorMessage = ""; web.loadUrl(currentUrl.ifBlank { app.url }) }) {
                                            Text(bt(R.string.msg_70dd895b66db))
                                        }
                                    }
                                }
                            } else {
                                AndroidView(
                                    factory = {
                                        (refreshContainer.parent as? ViewGroup)?.removeView(refreshContainer)
                                        refreshContainer
                                    },
                                    update = {
                                        it.setColorSchemeColors(cs.primary.toArgb())
                                        it.setProgressBackgroundColorSchemeColor(cs.surface.toArgb())
                                    },
                                    modifier = Modifier.fillMaxSize().testTag("webapp-web-content"),
                                )
                            }
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                }
            }

            fullScreenView?.let { custom ->
                DisposableEffect(custom) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                (custom.parent as? ViewGroup)?.removeView(custom)
                                addView(custom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            pendingPrompt?.let { prompt ->
                var input by remember(prompt) { mutableStateOf(prompt.initialInput.orEmpty()) }
                AlertDialog(
                    onDismissRequest = {
                        pendingPrompt = null
                        prompt.onCancel()
                    },
                    containerColor = cs.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    title = { Text(prompt.title) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(prompt.message)
                            if (prompt.initialInput != null) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(bt(R.string.msg_1cf2c46210c9)) },
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingPrompt = null
                            prompt.onConfirm(input)
                        }) { Text(prompt.confirmLabel) }
                    },
                    dismissButton = if (prompt.showCancel) {
                        {
                            TextButton(onClick = {
                                pendingPrompt = null
                                prompt.onCancel()
                            }) { Text(prompt.cancelLabel) }
                        }
                    } else null,
                )
            }
        }
    }
}
