package tw.techtarian.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

class MainActivity:AppCompatActivity(){
    lateinit var controller:BrowserController
    lateinit var bookmarkSync:BookmarkSync
    private lateinit var store:BrowserStore
    private var browserReady=false
    private var incomingIntentPending=false
    internal val imageDownloads=BrowserImageDownloads(this){message->if(::controller.isInitialized)controller.notice=message}
    internal val imageActions=BrowserImageActions(this)
    internal val pageDownloads=BrowserPageDownloads(this)
    internal val websiteLocation=WebsiteLocation(this)
    private val consent=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->bookmarkSync.consent(result.data)}
    private val importBookmarks=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){
            val result=runCatching{
                val data=contentResolver.openInputStream(uri)?.use{it.readBounded(8_000_001)}?:error(bt(R.string.msg_824602facd05))
                require(data.size<=8_000_000){bt(R.string.msg_0aae4ed21a92)}
                val rows=BookmarkFormat.parseHtml(String(data,Charsets.UTF_8));require(rows.isNotEmpty()){ bt(R.string.msg_7193e8524414) };rows
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){result.onSuccess{rows->
                controller.prompts.confirm(bt(R.string.msg_01fd7b112086 ,rows.size),bt(R.string.msg_d11135ca0ab4),bt(R.string.msg_20809c5d8596)){
                    runCatching{store.bookmarkStore.importRows(rows)}.onSuccess{count->
                        controller.revision++;controller.sheet="bookmarks"
                        controller.notice=if(count>0)bt(R.string.msg_ef3ade3905f2 ,count)else bt(R.string.msg_3778200c90e5)
                    }.onFailure{controller.notice=bt(R.string.msg_f7fbddb4f56e)}
                }
            }.onFailure{controller.notice=it.localizedMessage?:bt(R.string.msg_06da96fbc1c9)}}
        }
    }
    private val exportBookmarks=registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")){uri->if(uri!=null)runCatching{contentResolver.openOutputStream(uri)?.use{it.write(BookmarkFormat.html(store.bookmarkStore.visible()).toByteArray())}?:error(bt(R.string.msg_90b894bfd26f))}.onSuccess{controller.notice=bt(R.string.msg_7377414b1f6d)}.onFailure{controller.notice=bt(R.string.msg_fef661198324)}}
    fun importBookmarks(){importBookmarks.launch(arrayOf("text/html","application/xhtml+xml","text/plain","application/octet-stream"))}
    fun exportBookmarks(){exportBookmarks.launch("ChengJing-Bookmarks.html")}
    private val files=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->controller.fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data));controller.fileCallback=null}
    override fun onCreate(savedInstanceState:Bundle?){
        delegate.localNightMode=BrowserAppearance.mode(BrowserAppearance.savedChoice(this))
        super.onCreate(savedInstanceState);AppLanguages.initialize(this);setTheme(R.style.AppTheme);enableEdgeToEdge()
        updateSplashTheme(BrowserAppearance.savedChoice(this))
        androidx.core.content.ContextCompat.registerReceiver(this,languageReceiver,android.content.IntentFilter(Intent.ACTION_LOCALE_CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        imageActions.initialize()
        pageDownloads.initialize()
        if(android.os.Build.VERSION.SDK_INT>=31)splashScreen.setOnExitAnimationListener{it.remove()}
        @Suppress("DEPRECATION")
        val taskIcon=if(android.os.Build.VERSION.SDK_INT>=33)android.app.ActivityManager.TaskDescription.Builder().setLabel(bt(R.string.msg_bb11ab4df9b9)).setIcon(R.mipmap.ic_launcher).build()
            else android.app.ActivityManager.TaskDescription(bt(R.string.msg_bb11ab4df9b9),R.mipmap.ic_launcher)
        setTaskDescription(taskIcon)
        store=BrowserStore(this);controller=BrowserController(this,store)
        bookmarkSync=BookmarkSync(this,store.bookmarkStore)
        bookmarkSync.launchConsent={consent.launch(it)}
        controller.chooseFiles={files.launch(it)}
        val systemDark=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark=systemDark
        val launch=BrowserLaunchSurface(this,dark)
        WindowCompat.getInsetsController(window,window.decorView).apply{
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }
        setContentView(launch)
        // Allow a native frame before the expensive first WebView is constructed.
        // No sleep, minimum display time, network wait, or separate splash Activity.
        launch.doOnPreDraw{launch.post{
            if(!isFinishing&&!isDestroyed&&!browserReady){
                controller.restoreTabs()
                if(savedInstanceState?.getBoolean("browser-launch-consumed")!=true||incomingIntentPending)acceptIncomingLink(intent)
                browserReady=true
                setContent { BrowserApp(controller,store) }
                if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))bookmarkSync.resume()
            }
        }}
    }
    internal fun applyLanguage(choice:String){
        AppLanguages.select(this,choice)
        if(browserReady)controller.refreshLanguage()
    }
    private fun refreshLanguageFromDevice(){
        val previous=AppLanguages.currentTag
        AppLanguages.refresh(this)
        if(browserReady&&previous!=AppLanguages.currentTag)controller.refreshLanguage()
    }
    private val languageReceiver=object:android.content.BroadcastReceiver(){
        override fun onReceive(context:android.content.Context?,intent:Intent?){refreshLanguageFromDevice()}
    }
    internal fun applyAppearance(choice:String){
        store.theme=choice
        syncAppearance(choice)
    }
    internal fun syncAppearance(choice:String){
        updateSplashTheme(choice)
        delegate.localNightMode=BrowserAppearance.mode(choice)
        notifyWebAppearance(resources.configuration)
    }
    override fun onConfigurationChanged(configuration:android.content.res.Configuration){
        super.onConfigurationChanged(configuration)
        refreshLanguageFromDevice()
        notifyWebAppearance(configuration)
    }
    private fun notifyWebAppearance(configuration:android.content.res.Configuration){
        // AppCompat can update resources without an Activity recreation. Detached tabs
        // do not receive the view-tree event, so forward it to every existing WebView.
        // This updates native color-scheme preferences without reloading any page.
        if(browserReady)controller.tabs.forEach{it.web.dispatchConfigurationChanged(configuration)}
    }
    private fun updateSplashTheme(choice:String){
        if(android.os.Build.VERSION.SDK_INT>=31)splashScreen.setSplashScreenTheme(when(choice){
            "dark"->R.style.AppTheme_Starting_Dark;"light"->R.style.AppTheme_Starting_Light;else->R.style.AppTheme_Starting
        })
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        if(browserReady)acceptIncomingLink(intent)else incomingIntentPending=true
    }
    private fun acceptIncomingLink(incoming:Intent?) {
        incomingIntentPending=false
        val url=incoming?.dataString?.takeIf{it.startsWith("https://")||it.startsWith("http://")}?:return
        // Consume the launch URL so Activity recreation does not open the same link again.
        setIntent(Intent(incoming).setData(null))
        controller.prompts.cancel();controller.imagePreview=null;controller.exitFullscreen();controller.sheet=""
        controller.openExternalTab(url)
    }
    internal fun backFromPage(){
        if(controller.active?.isInitialNewTab()==true){
            // The new-tab page is the final Back stop; leave the app without adding/removing tabs.
            controller.persistTabs()
            if(!moveTaskToBack(true))finish()
            return
        }
        controller.backInBrowser()
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putBoolean("browser-launch-consumed",browserReady&&!incomingIntentPending)
        super.onSaveInstanceState(outState)
    }
    override fun onPause(){
        websiteLocation.pause()
        super.onPause()
        // A backgrounded/closed launch must not replace saved tabs with an empty list.
        if(browserReady){controller.checkpointTabs();android.webkit.CookieManager.getInstance().flush()}
    }
    override fun onResume(){super.onResume();refreshLanguageFromDevice();websiteLocation.resume();if(browserReady){if(controller.tabs.isEmpty())controller.newTab(incognito=false);if(::bookmarkSync.isInitialized)bookmarkSync.resume()}}
    override fun onDestroy(){
        runCatching{unregisterReceiver(languageReceiver)};if(::bookmarkSync.isInitialized)bookmarkSync.destroy();websiteLocation.close();pageDownloads.close();imageActions.close();if(::controller.isInitialized)controller.destroy();super.onDestroy()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BrowserApp(c:BrowserController,store:BrowserStore){
    val theme=store.theme
    var addressAtBottom by remember{mutableStateOf(store.addressAtBottom)}
    var settingsCategory by remember{mutableStateOf("appearance")}
    var codeFocus by remember{mutableStateOf("")}
    val panelTrail=remember{mutableStateListOf<String>()}
    LaunchedEffect(c.sheet){
        val page=c.sheet
        if(page.isBlank()||page in setOf("bookmarks","favorites"))panelTrail.clear()
        else {val index=panelTrail.indexOf(page);if(index>=0){while(panelTrail.size>index+1)panelTrail.removeAt(panelTrail.lastIndex)}else panelTrail.add(page)}
    }
    val dark=isSystemInDarkTheme()
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val activity=c.context as MainActivity
    LaunchedEffect(theme){activity.syncAppearance(theme)}
    val active=c.active
    val finding=c.findInPage.isOpen
    c.revision
    val pageFavorite=c.favorites.forPage(active?.url.orEmpty())
    val address=remember(active?.id,active?.url){TextFieldState(active?.url.orEmpty(),initialSelection=TextRange.Zero)}
    var editingAddress by remember{mutableStateOf(false)}
    LaunchedEffect(finding){if(finding)editingAddress=false}
    LaunchedEffect(active?.id){editingAddress=false;focus.clearFocus(force=true);keyboard?.hide()}
    val suggestionRows=remember(address.text,c.revision,editingAddress,active?.incognito){if(editingAddress&&active?.incognito!=true)AddressHistory.suggestions(address.text.toString(),store.searches(),store.history())else emptyList()}
    LaunchedEffect(c.fullScreenView){if(c.fullScreenView!=null){c.findInPage.close();editingAddress=false;focus.clearFocus(force=true);keyboard?.hide()}}
    val cs=browserColorScheme()
    SideEffect{c.updatePrivacyWindow()}
    SideEffect{browserSystemBars(activity.window,activity.window.decorView,!dark&&c.fullScreenView==null,c.fullScreenView!=null)}
    LaunchedEffect(c){
        snapshotFlow{c.notice to c.sheet}.filter{it.first.isNotEmpty()&&it.second !in setOf("bookmarks","favorites")}.collectLatest{(message,_)->
            c.notice=""
            snackbar.showSnackbar(message)
        }
    }
    BackHandler {
        when{
            c.prompts.current!=null->c.prompts.cancel()
            c.imagePreview!=null->c.imagePreview=null
            c.browsingDataCleaner.running->Unit
            c.fullScreenView!=null->c.exitFullscreen()
            finding->c.findInPage.close()
            c.sheet.isNotEmpty()->c.sheet=""
            active?.imageContent!=null->c.closeTab(active.id)
            c.eye->{c.stopEye();c.notice=bt(R.string.msg_b3adc3b24fd6)}
            else->activity.backFromPage()
        }
    }
    MaterialTheme(colorScheme=cs,typography=BrowserTypography){
        val addressBar:@Composable ()->Unit = {
            BrowserAddressBar(
                        address=address,editing=editingAddress,onFocus={editingAddress=it},
                        loading=(active?.progress?:100)<100,secure=active?.url?.startsWith("https:")==true&&active.error.isEmpty()&&active.certificateWarning.isEmpty(),
                        certificateWarning=active?.certificateWarning?.isNotEmpty()==true,
                        certificateException=c.store.certificateException(active?.url.orEmpty()),
                        blank=active?.url.isNullOrEmpty(),tabs=c.activeTabCount,
                        onGo={c.navigate(address.text.toString());editingAddress=false;focus.clearFocus()},
                        onSecurity={focus.clearFocus();c.sheet="connection"},
                        onReload={if((active?.progress?:100)<100){active?.web?.stopLoading();active?.refreshContainer?.isRefreshing=false}else c.reload()},
                        onTabs={focus.clearFocus();c.openTabOverview()},
                        onNewTab={focus.clearFocus();editingAddress=false;c.newTab()},
                        showHome=c.home.enabled,
                        onHome={focus.clearFocus();editingAddress=false;keyboard?.hide();c.openHome()},
                    )
        }
        val controlsBar:@Composable ()->Unit = {
            BrowserControls(c){focus.clearFocus();editingAddress=false;keyboard?.hide()}
        }
        Surface(Modifier.fillMaxSize(),color=cs.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Column(Modifier.fillMaxSize()){
                    if(finding)FindInPageBar(c.findInPage)else if(addressAtBottom)controlsBar()else addressBar()
                    if(!finding&&!addressAtBottom&&editingAddress)AddressSuggestionPanel(c,suggestionRows){c.navigate(it);editingAddress=false;focus.clearFocus()}
                    if(!addressAtBottom&&(active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                    if(active?.incognito==true)Text(bt(R.string.msg_79606cdda212),Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(horizontal=18.dp,vertical=5.dp).testTag("incognito-indicator"),fontSize=11.sp,color=cs.onSurfaceVariant)
                    if(c.eye) {
                        Row(Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Visibility,null,tint=cs.primary,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                            Text(if(c.dirty)bt(R.string.msg_c37a45eae0ca ,c.draft?.rules?.size?:0)else bt(R.string.msg_8d44a1395388),Modifier.weight(1f),fontSize=12.sp,color=cs.onPrimaryContainer)
                            if(c.dirty)TextButton(onClick={c.saveDraft()}){Text(bt(R.string.msg_dba44ed3f54f))}
                            Tool(Icons.Outlined.Close,bt(R.string.msg_315a88f3155e)){c.stopEye()}
                        }
                        TextButton(onClick={c.aiElement=null;c.sheet="develop-ai"},modifier=Modifier.fillMaxWidth().height(48.dp).background(cs.primaryContainer)){
                            Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(bt(R.string.msg_9cde4de25fb1))
                        }
                    } else if(c.isException) {
                        Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Text(bt(R.string.msg_b28cb43f9599),Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={c.exception()}){Text(bt(R.string.msg_c550069b0749))}}
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.imageContent!=null)ImageViewer(c,active.imageContent!!,Modifier.fillMaxSize(),temporaryTab=true){c.closeTab(active.id)}
                        else if(active?.url.isNullOrEmpty()){if(active?.incognito==true)IncognitoHome()else BrowserHome(c,store)}
                        else if(active?.error?.isNotEmpty()==true)Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CloudOff,null,Modifier.size(48.dp),tint=cs.primary);Spacer(Modifier.height(24.dp));Text(bt(R.string.msg_60db4174fe1b),style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(12.dp));Text(active.error,color=cs.onSurfaceVariant);Spacer(Modifier.height(20.dp));Button(onClick={c.reload()}){Text(bt(R.string.msg_70dd895b66db))}}
                        else active?.let{tab->key(tab.id){AndroidView(factory={(tab.refreshContainer.parent as? android.view.ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer},update={it.setColorSchemeColors(cs.primary.toArgb());it.setProgressBackgroundColorSchemeColor(cs.surface.toArgb())},modifier=Modifier.fillMaxSize().testTag("web-content"))}}
                    }
                    if(!finding&&addressAtBottom){
                        if(editingAddress)AddressSuggestionPanel(c,suggestionRows){c.navigate(it);editingAddress=false;focus.clearFocus()}
                        if((active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                        addressBar()
                    }else if(!finding)controlsBar()
                }
                SnackbarHost(snackbar,Modifier.testTag("browser-notices").align(Alignment.BottomCenter).padding(bottom=70.dp))
            }
        }
        if(c.sheet in setOf("bookmarks","favorites"))LibraryScreen(c)
        c.imagePreview?.let{ImagePreviewDialog(c,it)}
        if(c.sheet=="clear-browsing-data")DeleteBrowsingDataDialog(c)
        if(c.sheet in setOf("tabs","history","downloads"))BrowserPanel(c){
            PanelHeader(c){c.sheet="menu"}
            when(c.sheet){"tabs"->TabOverview(c);"history"->HistoryScreen(c);"downloads"->DownloadsScreen(c)}
        }
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites","tabs","history","downloads","clear-browsing-data")){
            BrowserPanel(c){
                    PanelHeader(c){c.sheet=panelTrail.dropLast(1).lastOrNull()?:panelParent(c.sheet)}
                    if(c.sheet=="settings")SettingsCategories(settingsCategory){settingsCategory=it}
                    key(c.sheet,if(c.sheet=="settings")settingsCategory else ""){
                        Column(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("panel-content").verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=4.dp,bottom=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(20.dp)){
                    when(c.sheet){
                        "menu"->BrowserMainMenu(c)
                        "connection"->ConnectionPanel(c)
                        "privacy"->PrivacyPanel(c)
                        "legal"->LicensePanel(c)
                        "eye"->EyePanel(c)
                        "selection"->SelectionPanel(c)
                        "element-editor"->ElementEditor(c)
                        "develop-ai"->DeveloperAiPanel(c){settingsCategory="ai";c.sheet="settings"}
                        "rules"->RulePanel(c)
                        "rule-editor"->SavedRuleEditor(c)
                        "rule-ai"->c.ruleEdit?.let{DeveloperAiPanel(c,revision=it){settingsCategory="ai";c.sheet="settings"}}
                        "code"->CodePanel(c,codeFocus)
                        "ai"->DeveloperAiPanel(c){settingsCategory="ai";c.sheet="settings"}
                        "settings"->SettingsPanel(settingsCategory,store,theme,{activity.applyAppearance(it)},c,addressAtBottom,{addressAtBottom=it;store.addressAtBottom=it})
                        "user-agent"->UserAgentPanel(c)
                        "search-engine"->SearchEnginePanel(c)
                        "inventory"->InventoryPanel(c)
                        "sync"->SyncPanel(c,store)
                        "domains"->{SheetTitle(bt(R.string.msg_f6b20e44de57),bt(R.string.msg_ed51242141b1))
                            val sites=store.all();if(sites.isEmpty())Text(bt(R.string.msg_f1c970051cf5))
                            sites.forEach{site->MenuRow(Icons.Outlined.Language,site.domain,bt(R.string.msg_baeab929c3dd ,site.rules.size+site.edits.size)){
                                if(c.domain==site.domain||c.newTab("https://${site.domain}/")!=null)c.sheet="rules"
                            }}
                        }
                        "blocked"->{SheetTitle(bt(R.string.msg_9d08608859f8),bt(R.string.msg_a37dc280e3d8 ,active?.blockedTotal?:0))
                            active?.blockedEvents?.toList()?.forEach{event->
                                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                                    Text(event.kind,fontWeight=FontWeight.SemiBold)
                                    Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(event.time)),fontSize=12.sp,color=cs.onSurfaceVariant)
                                    if(event.url.isNotEmpty())Text(event.url,fontSize=12.sp,maxLines=3,overflow=TextOverflow.Ellipsis,color=cs.onSurfaceVariant)
                                    if(event.url.startsWith("https://")||event.url.startsWith("http://"))TextButton(onClick={c.navigate(event.url)}){Text(bt(R.string.msg_b3471f8f134f))}
                                }
                            }
                        }
                    }
                        }
                    }
            }
        }
        c.fullScreenView?.let{view->VideoFullscreen(c,view)}
        BrowserPromptHost(c)
    }
}

@Composable private fun Tool(icon:ImageVector,label:String,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit){IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp)){Icon(icon,label,Modifier.size(22.dp))}}
@Composable private fun SheetTitle(title:String,subtitle:String=""){
    if(subtitle.isNotBlank())Text(subtitle,Modifier.fillMaxWidth(),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun SwitchRow(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(end=12.dp)){Text(title,fontSize=16.sp);Text(description,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}}

@Composable private fun EyePanel(c:BrowserController){
    SheetTitle(bt(R.string.msg_9a5282c7dd65),c.domain)
    Button(onClick={if(c.eye)c.sheet=""else c.beginEye()},enabled=!c.isException,modifier=Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(if(c.eye)bt(R.string.msg_d48d785df0f0)else bt(R.string.msg_6579f0f4fbf3))}
    if(c.eye && c.dirty)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={c.undoDraft()},Modifier.weight(1f)){Text(bt(R.string.msg_6595fa6d2a4f))};Button(onClick={c.saveDraft();c.sheet=""},Modifier.weight(1f)){Text(bt(R.string.msg_cd2a4fc27eef))}}
    WebsiteAiEntry(c)
    MenuGroup(bt(R.string.msg_bb52909d1d93)){
        MenuRow(Icons.Outlined.Layers,bt(R.string.msg_905d98bd5535),bt(R.string.msg_35eeaf58d612)){if(!c.eye)c.beginEye();if(c.eye)c.sheet="inventory"}
        MenuRow(Icons.Outlined.Tune,bt(R.string.msg_a4d95a4ce539),bt(R.string.msg_0590277f2647)){c.sheet="rules"}
        MenuRow(Icons.Outlined.Code,bt(R.string.msg_b654f1e4475a)){c.sheet="code"}
    }
    MenuGroup(bt(R.string.msg_1db1bf486d98)){
        SwitchRow(bt(R.string.msg_830509b36cfe),bt(R.string.msg_6114c2b64ad4),c.site.guard){c.saveSite(c.site.copy(guard=it));c.notice=bt(R.string.msg_7007cf22f796)}
        SwitchRow(bt(R.string.msg_3603b8ca2f4f),bt(R.string.msg_63ba28a2e7b0),c.isException){c.exception()}
    }
    if(c.store.hasPrevious(c.domain))TextButton(onClick={c.restoreRules(c.domain);c.sheet=""}){Icon(Icons.AutoMirrored.Outlined.Undo,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(bt(R.string.msg_e2ddf71b1142))}
}
@Composable private fun SelectionPanel(c:BrowserController){
    val selection=c.selection?:return
    val scope=rememberCoroutineScope()
    WebsiteAiEntry(c)
    Text(bt(R.string.msg_97efebc05b15 ,selection.label),fontSize=16.sp,fontWeight=FontWeight.Medium)
    Text(bt(R.string.msg_f36fed06504d ,selection.width,selection.height,selection.count),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(selection.frame)Text(bt(R.string.msg_bf8f470b6f54),fontSize=12.sp)
    MenuGroup(bt(R.string.msg_2bc121aa5664)){
        MenuRow(Icons.Outlined.Code,bt(R.string.msg_d1d6117a0915),bt(R.string.msg_6791c9f39720)){c.editingHtml=false;c.sheet="element-editor"}
        MenuRow(Icons.Outlined.VisibilityOff,bt(R.string.msg_704bb9551f08),bt(R.string.msg_cfe9a42f67d5)){scope.launch{c.removeSelected()}}
        MenuRow(Icons.Outlined.Edit,bt(R.string.msg_5bc8760e7c08),bt(R.string.msg_eb10b6a32675)){c.editingHtml=true;c.sheet="element-editor"}
        MenuRow(Icons.Outlined.AutoAwesome,bt(R.string.msg_31d97b13527a),bt(R.string.msg_ce96b28b44db)){c.aiElement=selection;c.sheet="develop-ai"}
    }
    if(selection.canParent)OutlinedButton(onClick={c.parentSelection()},Modifier.fillMaxWidth()){Text(bt(R.string.msg_2c0eb0d16c95))}
    TextButton(onClick={c.selection=null;c.sheet=""},Modifier.fillMaxWidth()){Text(bt(R.string.msg_2b0197cc2a08))}
}
@Composable private fun RulePanel(c:BrowserController){
    val site=c.site
    SheetTitle(bt(R.string.msg_58f73b892e2d),c.domain)
    if(site.css.isNotBlank()||site.js.isNotBlank()||site.html.isNotBlank())MenuGroup(bt(R.string.msg_76ae194967b7)){
        MenuRow(Icons.Outlined.Code,bt(R.string.msg_b654f1e4475a),bt(R.string.msg_b5ac67412c4e)){c.editSavedRule()}
    }
    if(site.edits.isNotEmpty())MenuGroup(bt(R.string.msg_d10980fb5a78)){
        site.edits.forEach{edit->MenuRow(Icons.Outlined.Code,if(edit.mode=="replace")bt(R.string.msg_96bf25ee29d2)else bt(R.string.msg_96f3303604ab),edit.selector){c.editSavedRule(edit.id)}}
    }
    if(site.rules.isNotEmpty())MenuGroup(bt(R.string.msg_27688f7df99c)){
        site.rules.forEach{rule->Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(rule.label,fontSize=14.sp);Text(rule.selector,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Tool(Icons.AutoMirrored.Outlined.Undo,bt(R.string.msg_215e19833aad ,rule.label)){c.saveSite(c.site.copy(rules=c.site.rules.filterNot{it.selector==rule.selector}));c.notice=bt(R.string.msg_f9aa2d52b8f2)}}}
    }
    if(site.rules.isEmpty()&&site.edits.isEmpty()&&site.css.isBlank()&&site.js.isBlank()&&site.html.isBlank())Text(bt(R.string.msg_4d38e86915a8))
    SwitchRow(bt(R.string.msg_60c4ef25cb60),bt(R.string.msg_96a669b3fccc),site.unlockScroll){c.saveSite(site.copy(unlockScroll=it))}
    Text(bt(R.string.msg_c6ee9069ad36),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun CodePanel(c:BrowserController,initialFocus:String){
    val original=remember{c.draft?:c.site};val tabId=remember{c.activeId};val includeDraft=remember{c.dirty}
    var css by remember{mutableStateOf(original.css)};var js by remember{mutableStateOf(original.js)};var html by remember{mutableStateOf(original.html)};var error by remember{mutableStateOf("")}
    val cssFocus=remember{FocusRequester()};val jsFocus=remember{FocusRequester()}
    LaunchedEffect(Unit){when(initialFocus){"css"->cssFocus.requestFocus();"js"->jsFocus.requestFocus()}}
    SheetTitle(bt(R.string.msg_e13bbc197118),original.domain)
    Text(bt(R.string.msg_1e9b4e76ed63),fontSize=13.sp,lineHeight=21.sp)
    if(includeDraft)Text(bt(R.string.msg_b2e95f75aae0),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
    OutlinedTextField(css,{css=it;error=""},label={Text(bt(R.string.msg_9e1d4222ab1b))},placeholder={Text("main { line-height: 1.8; }")},modifier=Modifier.fillMaxWidth().testTag("custom-css-input").focusRequester(cssFocus),textStyle=androidx.compose.ui.text.TextStyle(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),minLines=4,maxLines=7)
    OutlinedTextField(js,{js=it;error=""},label={Text("JavaScript")},placeholder={Text(bt(R.string.msg_628f36411710))},modifier=Modifier.fillMaxWidth().testTag("custom-js-input").focusRequester(jsFocus),textStyle=androidx.compose.ui.text.TextStyle(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),minLines=4,maxLines=7)
    CodeInput(bt(R.string.msg_b22612d48c02),html,{html=it},"custom-html-input",bt(R.string.msg_5bec1d40904a))
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    Button(onClick={
        if(c.activeId!=tabId||c.domain!=original.domain){error=bt(R.string.msg_63cf6247c550)}
        else runCatching{c.saveSite(original.copy(css=css,js=js,html=html),reload=true)}.onSuccess{c.sheet="";c.notice=bt(R.string.msg_213c519f9a8d)}.onFailure{error=bt(R.string.msg_7106b4590cce ,it.localizedMessage)}
    },Modifier.fillMaxWidth()){Text(bt(R.string.msg_d5ae259c7a80))}
}
@Composable private fun InventoryPanel(c:BrowserController){
    var elements by remember{mutableStateOf<List<JSONObject>>(emptyList())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit){val raw=c.js("window.__chengjingEye?.inventory()");elements=runCatching{val a=JSONArray(raw);(0 until a.length()).map{a.getJSONObject(it)}}.getOrDefault(emptyList());loading=false}
    SheetTitle(bt(R.string.msg_905d98bd5535),bt(R.string.msg_817f13599962))
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(!loading&&elements.isEmpty())Text(bt(R.string.msg_2111f387ba60))
    elements.forEach{j->MenuRow(if(j.optBoolean("hidden"))Icons.Outlined.VisibilityOff else Icons.Outlined.Layers,j.optString("label"),if(j.optBoolean("hidden"))bt(R.string.msg_167bc0947798)else if(j.optBoolean("fixed"))bt(R.string.msg_dc2e23f523ad)else bt(R.string.msg_047f31f927fe)){c.chooseSelector(j.getString("selector"))}}
}
@Composable private fun SettingsPanel(category:String,store:BrowserStore,theme:String,onTheme:(String)->Unit,c:BrowserController,addressAtBottom:Boolean,onAddressPosition:(Boolean)->Unit){
    val scope=rememberCoroutineScope();var key by remember{mutableStateOf("")};var hasKey by remember{mutableStateOf(store.hasKey())};var model by remember{mutableStateOf(store.model)};var models by remember{mutableStateOf(OpenRouter.defaults)};var loading by remember{mutableStateOf(false)}
    when(category){
        "appearance"->{
            LanguageSettings(c.context as MainActivity)
            SettingsGroup(bt(R.string.msg_e9666224fbb7)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(Triple("light",bt(R.string.msg_dee60aee2501),Icons.Outlined.LightMode),Triple("dark",bt(R.string.msg_a6b75d068032),Icons.Outlined.DarkMode),Triple("system",bt(R.string.msg_6df543c4a401),Icons.Outlined.BrightnessAuto)).forEach{(id,label,icon)->AppearanceOption(label,theme==id,Modifier.weight(1f),icon=icon){onTheme(id)}}
                }
                Text(bt(R.string.msg_381e6e45713f),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsGroup(bt(R.string.msg_4afb08d71d8d)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    AppearanceOption(bt(R.string.msg_80a24fd604b8),!addressAtBottom,Modifier.weight(1f),addressBottom=false){onAddressPosition(false)}
                    AppearanceOption(bt(R.string.msg_629f91b2430d),addressAtBottom,Modifier.weight(1f),addressBottom=true){onAddressPosition(true)}
                }
                Text(if(addressAtBottom)bt(R.string.msg_b8b124ce8c68)else bt(R.string.msg_1ff1261a38e8),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "browsing"->{
            HomeSettings(c)
            MenuGroup(bt(R.string.msg_03c481a6ab85)){
                MenuRow(Icons.Outlined.Search,bt(R.string.msg_14bd2c678335),"${SearchEngines.label(store.searchSettings)} · ${SearchEngines.description(store.searchSettings)}"){c.sheet="search-engine"}
            }
            MenuGroup(bt(R.string.msg_4052ba88aa30)){
                MenuRow(Icons.Outlined.Language,bt(R.string.msg_586fbcc82bec),when(store.userAgentMode){"webview"->bt(R.string.msg_12acb73519ef);"custom"->bt(R.string.msg_0eac99137858);else->bt(R.string.msg_2a9edef138f8)}){c.sheet="user-agent"}
            }
            MenuGroup(bt(R.string.msg_93ad935f13c4)){
                MenuRow(Icons.Outlined.CloudSync,bt(R.string.msg_4d94c28a53c4)){c.sheet="sync"}
                MenuRow(Icons.Outlined.Folder,bt(R.string.msg_b1d2dc2fb2e6)){c.sheet="bookmarks"}
                MenuRow(Icons.Outlined.History,bt(R.string.msg_0baa9a64e9b1)){c.sheet="history"}
            }
            MenuGroup(bt(R.string.msg_c7845aa27319)){
                MenuRow(Icons.Outlined.Info,bt(R.string.msg_371d839ac65c)){c.sheet="legal"}
                MenuRow(Icons.Outlined.PrivacyTip,bt(R.string.msg_930e319f7845),bt(R.string.msg_56cb31e79eb1)){c.sheet="privacy"}
                MenuRow(Icons.Outlined.Science,bt(R.string.msg_e2bdb7ac0e1d),bt(R.string.msg_3a3d370702f1)){c.navigate("https://practice.chengjing.invalid/")}
            }
            Text(bt(R.string.msg_f329c206f45a ,BuildConfig.VERSION_NAME),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "ai"->{
            AiProviderPanel(c)
            if(store.aiProvider=="gemma")GemmaSetupPanel(c)else{
            SettingsGroup(bt(R.string.msg_d7dd64f83835)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(if(hasKey)Icons.Outlined.VerifiedUser else Icons.Outlined.Key,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text(if(hasKey)bt(R.string.msg_8831bba1231d)else bt(R.string.msg_454792f22b74),fontSize=15.sp,fontWeight=FontWeight.Medium);Text(if(hasKey)bt(R.string.msg_0b2e6e02b110)else bt(R.string.msg_b69f5d8ebb3b),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                OutlinedTextField(key,{key=it},label={Text(if(hasKey)bt(R.string.msg_a5cc92dedb6d)else"API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("openrouter-api-key"))
                if(key.isNotEmpty())Button(onClick={runCatching{store.saveKey(key)}.onSuccess{key="";hasKey=true;c.notice=bt(R.string.msg_349c23511e2e)}.onFailure{c.notice=bt(R.string.msg_9793d5aa34a9 ,it.localizedMessage)}},Modifier.fillMaxWidth()){Text(bt(R.string.msg_c9937515f133))}
                if(hasKey)TextButton(onClick={store.saveKey("");hasKey=false;c.notice=bt(R.string.msg_308f90d89d99)}){Text(bt(R.string.msg_4df897d4de83))}
            }
            SettingsGroup(bt(R.string.msg_c98e118e0a43)){
                models.forEach{id->
                    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(if(model==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background).clickable{model=id;store.model=id}.padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(selected=model==id,onClick={model=id;store.model=id})
                        Column(Modifier.weight(1f)){
                            Text(when{ id.startsWith("deepseek/")->"DeepSeek";id.startsWith("google/")->"Gemini";id.startsWith("openai/")->"GPT";else->id.substringBefore('/')},fontSize=14.sp,fontWeight=FontWeight.Medium)
                            Text(id,fontSize=11.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(model,{model=it;store.model=it},label={Text(bt(R.string.msg_4e8050ee2fde))},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(enabled=!loading,onClick={loading=true;scope.launch{runCatching{OpenRouter().models()}.onSuccess{models=it;c.notice=bt(R.string.msg_33190323da75)}.onFailure{c.notice=bt(R.string.msg_a822357ce405)};loading=false}}){Text(if(loading)bt(R.string.msg_a0bacec44f47)else bt(R.string.msg_233cb3dae5da))}
            }
            Text(bt(R.string.msg_8d8cdcd65552),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    EngineVersionPanel(c)
}
@Composable private fun SyncPanel(c:BrowserController,store:BrowserStore){
    val sync=(c.context as MainActivity).bookmarkSync
    LaunchedEffect(sync){sync.resume()}
    c.revision
    SheetTitle(bt(R.string.msg_4d94c28a53c4),bt(R.string.msg_f1f50baf49b4))
    Surface(modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(if(sync.busy)Icons.Outlined.CloudSync else if(!sync.connected||sync.details.isNotBlank())Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,null,tint=if(sync.requiresAttention)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(sync.status,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("sync-status"))
            if(sync.details.isNotBlank())Text(sync.details,fontSize=13.sp,color=if(sync.requiresAttention)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=21.sp,modifier=Modifier.testTag("sync-details"))
            if(sync.connected)Text(store.bookmarkStore.accountLabel,fontSize=13.sp)
            if(store.bookmarkStore.lastSync>0)Text(bt(R.string.msg_dbf504aee158)+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(store.bookmarkStore.lastSync)),fontSize=12.sp)
        }
    }
    Text(bt(R.string.msg_bb02b225cf7e),fontSize=14.sp,lineHeight=23.sp)
    SettingsGroup(bt(R.string.msg_da8c7e82d56f)){
        Text(bt(R.string.msg_462b5f8ed149),fontSize=13.sp,lineHeight=21.sp)
        Text(bt(R.string.msg_4c197d7a57af),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var siteSync by remember{mutableStateOf(store.syncSiteSettings)}
    SettingsGroup(bt(R.string.msg_bd81a5f18ff4)){
        Text(bt(R.string.msg_55f5a4c4b0b3),fontSize=13.sp,lineHeight=21.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(bt(R.string.msg_86b823816a78),Modifier.weight(1f),fontSize=15.sp)
            Switch(siteSync,{value->store.syncSiteSettings=value;siteSync=value;if(sync.connected)sync.changed()},enabled=!sync.busy,modifier=Modifier.testTag("sync-site-settings"))
        }
        Text(bt(R.string.msg_da8b0b8c9bc6),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(bt(R.string.msg_d9c03b110d35),fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Button(enabled=!sync.busy,onClick={sync.authorize(true)},modifier=Modifier.fillMaxWidth().height(50.dp)){
        if(sync.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
        Text(if(sync.busy)bt(R.string.msg_d166e71ff3ae)else if(sync.connected)bt(R.string.msg_36e7a43a1c67)else bt(R.string.msg_aee33ff22038))
    }
    if(sync.connected)TextButton(onClick={sync.disconnect()},Modifier.fillMaxWidth()){Text(bt(R.string.msg_9746a274324c))}
    Text(bt(R.string.msg_b69bae05d706),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun UserAgentPanel(c:BrowserController){
    var mode by remember{mutableStateOf(c.store.userAgentMode)}
    var custom by remember{mutableStateOf(c.store.customUserAgent)}
    var error by remember{mutableStateOf("")}
    SheetTitle(bt(R.string.msg_871eaa84c97d),bt(R.string.msg_efb208e781f4))
    listOf("chrome" to bt(R.string.msg_7fd85e60aef6),"webview" to bt(R.string.msg_12acb73519ef),"custom" to bt(R.string.msg_3138f64b4547)).forEach{(value,label)->
        Row(Modifier.fillMaxWidth().clickable{mode=value},verticalAlignment=Alignment.CenterVertically){RadioButton(mode==value,{mode=value});Text(label,fontSize=15.sp)}
    }
    if(mode=="custom")OutlinedTextField(custom,{custom=it},Modifier.fillMaxWidth().testTag("custom-user-agent"),label={Text("User-Agent")},minLines=3,maxLines=5,supportingText={Text(bt(R.string.msg_b1c83027f591))})
    Text(bt(R.string.msg_efa2a61e6723),fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    Button(onClick={runCatching{c.setUserAgent(mode,custom)}.onSuccess{c.sheet="";c.notice=bt(R.string.msg_73b390a73f19)}.onFailure{error=it.localizedMessage?:bt(R.string.msg_a51b36be9b6a)}},Modifier.fillMaxWidth()){Text(bt(R.string.msg_bb7b020c8cf9))}
    Text(bt(R.string.msg_7818a40d2a66 ,androidx.webkit.WebViewCompat.getCurrentWebViewPackage(c.context)?.versionName?:bt(R.string.msg_4d8c1c5b4283)),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
