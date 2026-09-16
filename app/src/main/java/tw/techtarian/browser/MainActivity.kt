package tw.techtarian.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
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

class MainActivity:ComponentActivity(){
    lateinit var controller:BrowserController
    lateinit var bookmarkSync:BookmarkSync
    private lateinit var store:BrowserStore
    private var browserReady=false
    internal val imageDownloads=BrowserImageDownloads(this){message->if(::controller.isInitialized)controller.notice=message}
    private val consent=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->bookmarkSync.consent(result.data)}
    private val importBookmarks=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){
            val result=runCatching{
                val data=contentResolver.openInputStream(uri)?.use{it.readBounded(8_000_001)}?:error("無法開啟書籤檔案")
                require(data.size<=8_000_000){"書籤檔案超過 8 MB，請分批匯入"}
                val rows=BookmarkFormat.parseHtml(String(data,Charsets.UTF_8));require(rows.isNotEmpty()){ "檔案中找不到 HTTP / HTTPS 書籤" };rows
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){result.onSuccess{rows->
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("匯入 ${rows.size} 個書籤？").setMessage("會保留資料夾；已存在的書籤不會重複新增。").setNegativeButton("取消",null).setPositiveButton("匯入"){_,_->
                    runCatching{store.bookmarkStore.importRows(rows)}.onSuccess{count->
                        controller.revision++;controller.sheet="bookmarks"
                        controller.notice=if(count>0)"已匯入 $count 個書籤"else"這些書籤已經匯入，不會重複新增"
                    }.onFailure{controller.notice="匯入未完成，請稍後再試"}
                }.show()
            }.onFailure{controller.notice=it.localizedMessage?:"匯入未完成"}}
        }
    }
    private val exportBookmarks=registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")){uri->if(uri!=null)runCatching{contentResolver.openOutputStream(uri)?.use{it.write(BookmarkFormat.html(store.bookmarkStore.visible()).toByteArray())}?:error("無法寫入檔案")}.onSuccess{controller.notice="書籤已匯出"}.onFailure{controller.notice="書籤匯出失敗"}}
    fun importBookmarks(){importBookmarks.launch(arrayOf("text/html","application/xhtml+xml","text/plain","application/octet-stream"))}
    fun exportBookmarks(){exportBookmarks.launch("ChengJing-Bookmarks.html")}
    private val files=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->controller.fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data));controller.fileCallback=null}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(android.os.Build.VERSION.SDK_INT>=31)splashScreen.setOnExitAnimationListener{it.remove()}
        @Suppress("DEPRECATION")
        val taskIcon=if(android.os.Build.VERSION.SDK_INT>=33)android.app.ActivityManager.TaskDescription.Builder().setLabel("澄境瀏覽器").setIcon(R.mipmap.ic_launcher).build()
            else android.app.ActivityManager.TaskDescription("澄境瀏覽器",R.mipmap.ic_launcher)
        setTaskDescription(taskIcon)
        store=BrowserStore(this);controller=BrowserController(this,store)
        bookmarkSync=BookmarkSync(this,store.bookmarkStore)
        bookmarkSync.launchConsent={consent.launch(it)}
        controller.chooseFiles={files.launch(it)}
        val systemDark=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark=store.theme=="dark"||(store.theme=="system"&&systemDark)
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
                val incoming=intent?.dataString
                if(incoming?.startsWith("https://")==true||incoming?.startsWith("http://")==true)controller.newTab(incoming,incognito=false) else controller.restoreTabs()
                browserReady=true
                setContent { BrowserApp(controller,store) }
                if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))bookmarkSync.resume()
            }
        }}
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        if(browserReady)intent.dataString?.let{if(it.startsWith("https://")||it.startsWith("http://"))controller.newTab(it,incognito=false)}
    }
    override fun onPause(){
        super.onPause()
        // A backgrounded/closed launch must not replace saved tabs with an empty list.
        if(browserReady){android.webkit.CookieManager.getInstance().flush();controller.persistTabs()}
    }
    override fun onResume(){super.onResume();if(browserReady&&::bookmarkSync.isInitialized)bookmarkSync.resume()}
    override fun onDestroy(){if(::bookmarkSync.isInitialized)bookmarkSync.destroy();if(::controller.isInitialized)controller.destroy();super.onDestroy()}
}

private val Light=lightColorScheme(primary=Color(0xFF147A64),onPrimary=Color(0xFFFFFDF7),secondaryContainer=Color(0xFFD9F0E9),onSecondaryContainer=Color(0xFF124D3F),primaryContainer=Color(0xFFD9F0E9),onPrimaryContainer=Color(0xFF124D3F),background=Color(0xFFF1EEE7),onBackground=Color(0xFF1D2925),surface=Color(0xFFFBFAF6),onSurface=Color(0xFF1D2925),surfaceVariant=Color(0xFFEAE6DC),onSurfaceVariant=Color(0xFF59665F),outline=Color(0xFF8A958B),outlineVariant=Color(0xFFD0CBC0),error=Color(0xFFB43D38))
private val Dark=darkColorScheme(primary=Color(0xFF69DFC0),onPrimary=Color(0xFF0E372C),secondaryContainer=Color(0xFF173F35),onSecondaryContainer=Color(0xFFD9F0E9),primaryContainer=Color(0xFF173F35),onPrimaryContainer=Color(0xFFD9F0E9),background=Color(0xFF0F1513),onBackground=Color(0xFFF2EFE7),surface=Color(0xFF1A2420),onSurface=Color(0xFFF2EFE7),surfaceVariant=Color(0xFF26332E),onSurfaceVariant=Color(0xFFB1B8B0),outline=Color(0xFF82998E),outlineVariant=Color(0xFF394842),error=Color(0xFFFF9188))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BrowserApp(c:BrowserController,store:BrowserStore){
    var theme by remember{mutableStateOf(store.theme)}
    var addressAtBottom by remember{mutableStateOf(store.addressAtBottom)}
    var settingsCategory by remember{mutableStateOf("appearance")}
    var codeFocus by remember{mutableStateOf("")}
    val panelTrail=remember{mutableStateListOf<String>()}
    LaunchedEffect(c.sheet){
        val page=c.sheet
        if(page.isBlank()||page in setOf("bookmarks","favorites"))panelTrail.clear()
        else {val index=panelTrail.indexOf(page);if(index>=0){while(panelTrail.size>index+1)panelTrail.removeAt(panelTrail.lastIndex)}else panelTrail.add(page)}
    }
    val dark=theme=="dark"||(theme=="system"&&isSystemInDarkTheme())
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val activity=c.context as MainActivity
    val active=c.active
    c.revision
    val pageFavorite=c.favorites.forPage(active?.url.orEmpty())
    var address by remember(active?.id,active?.url){mutableStateOf(TextFieldValue(active?.url.orEmpty()))}
    var editingAddress by remember{mutableStateOf(false)}
    val suggestionRows=remember(address.text,c.revision,editingAddress,active?.incognito){if(editingAddress&&active?.incognito!=true)AddressHistory.suggestions(address.text,store.searches(),store.history())else emptyList()}
    LaunchedEffect(c.fullScreenView){if(c.fullScreenView!=null){editingAddress=false;focus.clearFocus(force=true);keyboard?.hide()}}
    val cs=if(dark)Dark else Light
    SideEffect{c.updatePrivacyWindow()}
    SideEffect{WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightStatusBars=!dark;WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightNavigationBars=!dark}
    LaunchedEffect(c){
        snapshotFlow{c.notice to c.sheet}.filter{it.first.isNotEmpty()&&it.second !in setOf("bookmarks","favorites")}.collectLatest{(message,_)->
            c.notice=""
            snackbar.showSnackbar(message)
        }
    }
    BackHandler {
        when{
            c.fullScreenView!=null->c.exitFullscreen()
            c.sheet.isNotEmpty()->c.sheet=""
            c.eye->{c.stopEye();c.notice="已取消尚未儲存的預覽"}
            active?.web?.canGoBack()==true->active.web.goBack()
            active?.url?.isNotEmpty()==true->{c.newTab();c.closeTab(active.id)}
            else->activity.moveTaskToBack(true)
        }
    }
    MaterialTheme(colorScheme=cs,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp),titleMedium=androidx.compose.ui.text.TextStyle(fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold))){
        val addressBar:@Composable ()->Unit = {
            BrowserAddressBar(
                        address=address,onAddress={address=it},editing=editingAddress,onFocus={editingAddress=it},
                        loading=(active?.progress?:100)<100,secure=active?.url?.startsWith("https:")==true&&active.error.isEmpty()&&active.certificateWarning.isEmpty(),
                        certificateWarning=active?.certificateWarning?.isNotEmpty()==true,
                        certificateException=c.store.certificateException(active?.url.orEmpty()),
                        blank=active?.url.isNullOrEmpty(),tabs=c.activeTabCount,
                        onGo={c.navigate(address.text);editingAddress=false;focus.clearFocus()},
                        onSecurity={focus.clearFocus();c.sheet="connection"},
                        onReload={if((active?.progress?:100)<100){active?.web?.stopLoading();active?.refreshContainer?.isRefreshing=false}else c.reload()},
                        onTabs={focus.clearFocus();c.openTabOverview()},
                        onNewTab={focus.clearFocus();editingAddress=false;c.newTab()},
                    )
        }
        val controlsBar:@Composable ()->Unit = {
            BrowserControls(c){focus.clearFocus();editingAddress=false;keyboard?.hide()}
        }
        Surface(Modifier.fillMaxSize(),color=cs.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Column(Modifier.fillMaxSize()){
                    if(addressAtBottom)controlsBar()else addressBar()
                    if(!addressAtBottom&&editingAddress)AddressSuggestionPanel(c,suggestionRows){c.navigate(it);editingAddress=false;focus.clearFocus()}
                    if(!addressAtBottom&&(active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                    if(active?.incognito==true)Text("無痕瀏覽",Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(horizontal=18.dp,vertical=5.dp).testTag("incognito-indicator"),fontSize=11.sp,color=cs.onSurfaceVariant)
                    if(c.eye) {
                        Row(Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Visibility,null,tint=cs.primary,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                            Text(if(c.dirty)"${c.draft?.rules?.size?:0} 條規則 · 尚未儲存"else"天眼已開啟 · 點選網站元件",Modifier.weight(1f),fontSize=12.sp,color=cs.onPrimaryContainer)
                            if(c.dirty)TextButton(onClick={c.saveDraft()}){Text("儲存")}
                            Tool(Icons.Outlined.Close,"離開天眼"){c.stopEye()}
                        }
                        TextButton(onClick={c.aiElement=null;c.sheet="develop-ai"},modifier=Modifier.fillMaxWidth().height(48.dp).background(cs.primaryContainer)){
                            Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("想做什麼嘗試？直接問 AI")
                        }
                    } else if(c.isException) {
                        Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Text("例外中 · 原始網站",Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={c.exception()}){Text("恢復規則")}}
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.url.isNullOrEmpty()){if(active?.incognito==true)IncognitoHome()else Home(c,store)}
                        else if(active?.error?.isNotEmpty()==true)Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CloudOff,null,Modifier.size(48.dp),tint=cs.primary);Spacer(Modifier.height(24.dp));Text("暫時連不上這個網站",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(12.dp));Text(active.error,color=cs.onSurfaceVariant);Spacer(Modifier.height(20.dp));Button(onClick={c.reload()}){Text("重新載入")}}
                        else active?.let{tab->key(tab.id){AndroidView(factory={(tab.refreshContainer.parent as? android.view.ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer},update={it.setColorSchemeColors(cs.primary.toArgb());it.setProgressBackgroundColorSchemeColor(cs.surface.toArgb())},modifier=Modifier.fillMaxSize().testTag("web-content"))}}
                    }
                    if(addressAtBottom){
                        if(editingAddress)AddressSuggestionPanel(c,suggestionRows){c.navigate(it);editingAddress=false;focus.clearFocus()}
                        if((active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                        addressBar()
                    }else controlsBar()
                }
                SnackbarHost(snackbar,Modifier.testTag("browser-notices").align(Alignment.BottomCenter).padding(bottom=70.dp))
            }
        }
        if(c.sheet in setOf("bookmarks","favorites"))LibraryScreen(c)
        if(c.sheet in setOf("tabs","history","downloads"))BrowserPanel(c){
            PanelHeader(c){c.sheet="menu"}
            when(c.sheet){"tabs"->TabOverview(c);"history"->HistoryScreen(c);"downloads"->DownloadsScreen(c)}
        }
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites","tabs","history","downloads")){
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
                        "code"->CodePanel(c,codeFocus)
                        "ai"->DeveloperAiPanel(c){settingsCategory="ai";c.sheet="settings"}
                        "settings"->SettingsPanel(settingsCategory,store,theme,{theme=it;store.theme=it},c,addressAtBottom,{addressAtBottom=it;store.addressAtBottom=it})
                        "user-agent"->UserAgentPanel(c)
                        "inventory"->InventoryPanel(c)
                        "sync"->SyncPanel(c,store)
                        "domains"->{SheetTitle("網域規則","你的選擇，留在你的手機。")
                            val sites=store.all();if(sites.isEmpty())Text("尚未儲存任何網域規則。")
                            sites.forEach{site->MenuRow(Icons.Outlined.Language,site.domain,"${site.rules.size} 條元件規則${if(site.guard)" · 跳轉防護"else""}"){c.navigate(if(site.domain=="practice.chengjing.invalid")"https://${site.domain}/"else"https://${site.domain}")}}
                        }
                        "find"->{var text by remember{mutableStateOf("")};SheetTitle("尋找頁面文字");OutlinedTextField(text,{text=it;active?.web?.findAllAsync(it)},label={Text("要找的文字")},modifier=Modifier.fillMaxWidth());Row{TextButton(onClick={active?.web?.findNext(false)}){Text("上一個")};TextButton(onClick={active?.web?.findNext(true)}){Text("下一個")};TextButton(onClick={active?.web?.clearMatches();c.sheet=""}){Text("完成")}}}
                        "blocked"->{SheetTitle("攔截紀錄","本分頁共 ${active?.blockedTotal?:0} 次，保留最近 30 筆。")
                            active?.blockedEvents?.toList()?.forEach{event->
                                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                                    Text(event.kind,fontWeight=FontWeight.SemiBold)
                                    Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(event.time)),fontSize=12.sp,color=cs.onSurfaceVariant)
                                    if(event.url.isNotEmpty())Text(event.url,fontSize=12.sp,maxLines=3,overflow=TextOverflow.Ellipsis,color=cs.onSurfaceVariant)
                                    if(event.url.startsWith("https://")||event.url.startsWith("http://"))TextButton(onClick={c.navigate(event.url)}){Text("自行開啟此網址")}
                                }
                            }
                        }
                    }
                        }
                    }
            }
        }
        c.fullScreenView?.let{view->VideoFullscreen(c,view)}
    }
}

@Composable private fun Home(c:BrowserController,store:BrowserStore){
    val cs=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=38.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.ic_launcher),"澄境瀏覽器",modifier=Modifier.size(56.dp));Spacer(Modifier.width(12.dp));Column{Text("澄境瀏覽器",fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text("CHENGJING BROWSER",fontSize=9.sp,letterSpacing=1.6.sp,color=cs.onSurfaceVariant)}}
        Spacer(Modifier.height(44.dp))
        Text("網頁，\n依你的習慣調整。",fontSize=35.sp,lineHeight=47.sp,fontWeight=FontWeight.Light,letterSpacing=(-1).sp)
        Spacer(Modifier.height(18.dp))
        Text("照常瀏覽，也能打開天眼，\n新增樣式、程式碼或調整元件。",fontSize=15.sp,lineHeight=25.sp,color=cs.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Text("快速前往",fontSize=12.sp,color=cs.onSurfaceVariant);Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            AssistChip(onClick={c.navigate("https://www.google.com")},label={Text("Google")},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(16.dp))})
            AssistChip(onClick={c.navigate("https://zh.wikipedia.org")},label={Text("維基百科")},leadingIcon={Icon(Icons.Outlined.Language,null,Modifier.size(16.dp))})
        }
        val favoriteRevision=c.revision
        val recentFavorites=remember(favoriteRevision){c.favorites.all().take(3)}
        if(recentFavorites.isNotEmpty()){
            Spacer(Modifier.height(18.dp));Text("繼續閱讀",fontSize=12.sp,color=cs.onSurfaceVariant)
            recentFavorites.forEach{favorite->Row(Modifier.fillMaxWidth().clickable{c.openFavorite(favorite)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){SiteIcon(c,favorite.url,28.dp);Spacer(Modifier.width(10.dp));Text(favorite.title,Modifier.weight(1f),fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(onClick={c.sheet="bookmarks"}){Icon(Icons.Outlined.Folder,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("書籤資料夾")}
            TextButton(onClick={c.sheet="favorites"}){Icon(Icons.Outlined.StarOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("所有收藏")}
        }
    }
}
@Composable private fun Tool(icon:ImageVector,label:String,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit){IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp)){Icon(icon,label,Modifier.size(22.dp))}}
@Composable private fun SheetTitle(title:String,subtitle:String=""){
    if(subtitle.isNotBlank())Text(subtitle,Modifier.fillMaxWidth(),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun SwitchRow(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(end=12.dp)){Text(title,fontSize=16.sp);Text(description,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}}

@Composable private fun EyePanel(c:BrowserController){
    SheetTitle("天眼",c.domain)
    Button(onClick={if(c.eye)c.sheet=""else c.beginEye()},enabled=!c.isException,modifier=Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(if(c.eye)"繼續選取元件"else"開啟天眼・選取元件")}
    if(c.eye && c.dirty)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={c.undoDraft()},Modifier.weight(1f)){Text("撤回上一個")};Button(onClick={c.saveDraft();c.sheet=""},Modifier.weight(1f)){Text("儲存規則")}}
    WebsiteAiEntry(c)
    MenuGroup("元件與程式碼"){
        MenuRow(Icons.Outlined.Layers,"結構清單","查看浮動、隱藏與內嵌元件"){if(!c.eye)c.beginEye();if(c.eye)c.sheet="inventory"}
        MenuRow(Icons.Outlined.Tune,"已儲存的網站修改","${c.site.rules.size+c.site.edits.size} 項 · 適用此網域與子網域"){c.sheet="rules"}
        MenuRow(Icons.Outlined.Code,"網站 CSS / JS / HTML"){c.sheet="code"}
    }
    MenuGroup("網站行為"){
        SwitchRow("防止跳轉與彈窗","登入流程受影響時可關閉。",c.site.guard){c.saveSite(c.site.copy(guard=it));c.notice="網域防護設定已儲存"}
        SwitchRow("暫時顯示原始網站","暫停此網域設定，直到關閉 App。",c.isException){c.exception()}
    }
    if(c.store.hasPrevious(c.domain))TextButton(onClick={c.restoreRules(c.domain);c.sheet=""}){Icon(Icons.AutoMirrored.Outlined.Undo,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("復原上一次儲存")}
}
@Composable private fun SelectionPanel(c:BrowserController){
    val selection=c.selection?:return
    val scope=rememberCoroutineScope()
    WebsiteAiEntry(c)
    Text("所選元件 · ${selection.label}",fontSize=16.sp,fontWeight=FontWeight.Medium)
    Text("${selection.width} × ${selection.height} · 此選擇器目前符合 ${selection.count} 個元件",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(selection.frame)Text("內嵌頁面可調整外框；跨來源的內部內容無法在此存取。",fontSize=12.sp)
    MenuGroup("想怎麼修改這個元件？"){
        MenuRow(Icons.Outlined.Code,"新增自訂 CSS / JS / HTML","加入樣式、互動或頁面內容"){c.editingHtml=false;c.sheet="element-editor"}
        MenuRow(Icons.Outlined.VisibilityOff,"移除此網站元件","先預覽，再選擇是否儲存"){scope.launch{c.removeSelected()}}
        MenuRow(Icons.Outlined.Edit,"修改這段代碼","編輯元件內部 HTML"){c.editingHtml=true;c.sheet="element-editor"}
        MenuRow(Icons.Outlined.AutoAwesome,"我這樣改對嗎？請 AI 檢查","僅針對所選元件提出修改"){c.aiElement=selection;c.sheet="develop-ai"}
    }
    if(selection.canParent)OutlinedButton(onClick={c.parentSelection()},Modifier.fillMaxWidth()){Text("選取外面一層")}
    TextButton(onClick={c.selection=null;c.sheet=""},Modifier.fillMaxWidth()){Text("改選其他元件")}
}
@Composable private fun RulePanel(c:BrowserController){
    val site=c.site
    SheetTitle("移除規則",c.domain)
    if(site.rules.isEmpty())Text("目前沒有移除規則。開啟天眼，點選不想看到的元件。")
    site.rules.forEach{rule->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(rule.label,fontSize=14.sp);Text(rule.selector,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Tool(Icons.AutoMirrored.Outlined.Undo,"恢復 ${rule.label}"){c.saveSite(c.site.copy(rules=c.site.rules.filterNot{it.selector==rule.selector}));c.notice="已恢復這個元件"}}}
    site.edits.forEach{edit->MenuRow(Icons.Outlined.Code,if(edit.mode=="replace")"編輯 HTML"else"自訂元件程式碼",edit.selector){c.saveSite(c.site.copy(edits=c.site.edits.filterNot{it.id==edit.id}),reload=true);c.notice="已恢復這項修改"}}
    SwitchRow("恢復頁面捲動","移除蓋版後仍無法捲動時，才開啟。",site.unlockScroll){c.saveSite(site.copy(unlockScroll=it))}
    Text("規則保存在本機。網域的結構如果改版，可以重新選取或請 AI 調整。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun CodePanel(c:BrowserController,initialFocus:String){
    val original=remember{c.draft?:c.site};val tabId=remember{c.activeId};val includeDraft=remember{c.dirty}
    var css by remember{mutableStateOf(original.css)};var js by remember{mutableStateOf(original.js)};var html by remember{mutableStateOf(original.html)};var error by remember{mutableStateOf("")}
    val cssFocus=remember{FocusRequester()};val jsFocus=remember{FocusRequester()}
    LaunchedEffect(Unit){when(initialFocus){"css"->cssFocus.requestFocus();"js"->jsFocus.requestFocus()}}
    SheetTitle("自訂程式碼",original.domain)
    Text("程式碼會套用到此網域及子網域。JavaScript 可讀取頁面並發出網路請求，請只使用信任的內容。例外模式會暫停套用。",fontSize=13.sp,lineHeight=21.sp)
    if(includeDraft)Text("儲存時會一併保留目前天眼預覽的元件修改。",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
    OutlinedTextField(css,{css=it;error=""},label={Text("CSS 樣式")},placeholder={Text("main { line-height: 1.8; }")},modifier=Modifier.fillMaxWidth().testTag("custom-css-input").focusRequester(cssFocus),minLines=4,maxLines=7)
    OutlinedTextField(js,{js=it;error=""},label={Text("JavaScript")},placeholder={Text("// 新增或調整頁面內容；每次載入後執行一次")},modifier=Modifier.fillMaxWidth().testTag("custom-js-input").focusRequester(jsFocus),minLines=4,maxLines=7)
    CodeInput("網站新增 HTML（放在頁面最後方）",html,{html=it},"custom-html-input","<p>我的閱讀提示</p>")
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    Button(onClick={
        if(c.activeId!=tabId||c.domain!=original.domain){error="頁面已變更，請關閉後重新開啟編輯器"}
        else runCatching{c.saveSite(original.copy(css=css,js=js,html=html),reload=true)}.onSuccess{c.sheet="";c.notice="已儲存並重新載入"}.onFailure{error="儲存失敗：${it.localizedMessage}"}
    },Modifier.fillMaxWidth()){Text("儲存並套用")}
}
@Composable private fun InventoryPanel(c:BrowserController){
    var elements by remember{mutableStateOf<List<JSONObject>>(emptyList())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit){val raw=c.js("window.__chengjingEye?.inventory()");elements=runCatching{val a=JSONArray(raw);(0 until a.length()).map{a.getJSONObject(it)}}.getOrDefault(emptyList());loading=false}
    SheetTitle("結構清單","包括原本看不見的元件。最多顯示 80 個浮動、隱藏及內嵌元件。")
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(!loading&&elements.isEmpty())Text("目前沒有找到這類元件；仍可直接點選網頁中的一般元件。")
    elements.forEach{j->MenuRow(if(j.optBoolean("hidden"))Icons.Outlined.VisibilityOff else Icons.Outlined.Layers,j.optString("label"),if(j.optBoolean("hidden"))"原本隱藏"else if(j.optBoolean("fixed"))"浮動／固定元件"else"內嵌頁面"){c.chooseSelector(j.getString("selector"))}}
}
@Composable private fun SettingsPanel(category:String,store:BrowserStore,theme:String,onTheme:(String)->Unit,c:BrowserController,addressAtBottom:Boolean,onAddressPosition:(Boolean)->Unit){
    val scope=rememberCoroutineScope();var key by remember{mutableStateOf("")};var hasKey by remember{mutableStateOf(store.hasKey())};var model by remember{mutableStateOf(store.model)};var models by remember{mutableStateOf(OpenRouter.defaults)};var loading by remember{mutableStateOf(false)}
    when(category){
        "appearance"->{
            SettingsGroup("主題"){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(Triple("light","淺色",Icons.Outlined.LightMode),Triple("dark","深色",Icons.Outlined.DarkMode),Triple("system","系統",Icons.Outlined.BrightnessAuto)).forEach{(id,label,icon)->AppearanceOption(label,theme==id,Modifier.weight(1f),icon=icon){onTheme(id)}}
                }
                Text("跟隨系統會依手機的深色設定切換。",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsGroup("網址列位置"){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    AppearanceOption("網址列在上方",!addressAtBottom,Modifier.weight(1f),addressBottom=false){onAddressPosition(false)}
                    AppearanceOption("網址列在下方",addressAtBottom,Modifier.weight(1f),addressBottom=true){onAddressPosition(true)}
                }
                Text(if(addressAtBottom)"網址列在下方，天眼與導覽工具列在上方。"else"網址列在上方，天眼與導覽工具列在下方。",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "browsing"->{
            MenuGroup("網站顯示"){
                MenuRow(Icons.Outlined.Language,"瀏覽器識別（User-Agent）",when(store.userAgentMode){"webview"->"原始 Android WebView";"custom"->"自訂識別";else->"Chrome 手機版"}){c.sheet="user-agent"}
            }
            MenuGroup("資料與同步"){
                MenuRow(Icons.Outlined.CloudSync,"Google 同步"){c.sheet="sync"}
                MenuRow(Icons.Outlined.Folder,"書籤資料夾"){c.sheet="bookmarks"}
                MenuRow(Icons.Outlined.History,"瀏覽紀錄"){c.sheet="history"}
            }
            MenuGroup("使用說明"){
                MenuRow(Icons.Outlined.Info,"第三方授權"){c.sheet="legal"}
                MenuRow(Icons.Outlined.PrivacyTip,"隱私與資料","資料用途、保存與刪除方式"){c.sheet="privacy"}
                MenuRow(Icons.Outlined.Science,"天眼練習場","練習調整元件、新增 CSS 與 JS"){c.navigate("https://practice.chengjing.invalid/")}
            }
            Text("澄境瀏覽器 ${BuildConfig.VERSION_NAME} · Android 版",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "ai"->{
            AiProviderPanel(c)
            if(store.aiProvider=="gemma")GemmaSetupPanel(c)else{
            SettingsGroup("OpenRouter 連線"){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(if(hasKey)Icons.Outlined.VerifiedUser else Icons.Outlined.Key,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text(if(hasKey)"金鑰已儲存"else"連結你的 AI",fontSize=15.sp,fontWeight=FontWeight.Medium);Text(if(hasKey)"已加密保存在這支手機"else"讓 AI 協助檢查天眼規則",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                OutlinedTextField(key,{key=it},label={Text(if(hasKey)"輸入新的 API Key 以替換"else"API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("openrouter-api-key"))
                if(key.isNotEmpty())Button(onClick={runCatching{store.saveKey(key)}.onSuccess{key="";hasKey=true;c.notice="API Key 已加密儲存"}.onFailure{c.notice="金鑰儲存失敗：${it.localizedMessage}"}},Modifier.fillMaxWidth()){Text("儲存 API Key")}
                if(hasKey)TextButton(onClick={store.saveKey("");hasKey=false;c.notice="API Key 已移除"}){Text("移除已儲存的 API Key")}
            }
            SettingsGroup("模型"){
                models.forEach{id->
                    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(if(model==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background).clickable{model=id;store.model=id}.padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(selected=model==id,onClick={model=id;store.model=id})
                        Column(Modifier.weight(1f)){
                            Text(when{ id.startsWith("deepseek/")->"DeepSeek";id.startsWith("google/")->"Gemini";id.startsWith("openai/")->"GPT";else->id.substringBefore('/')},fontSize=14.sp,fontWeight=FontWeight.Medium)
                            Text(id,fontSize=11.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(model,{model=it;store.model=it},label={Text("或自行輸入模型名稱")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(enabled=!loading,onClick={loading=true;scope.launch{runCatching{OpenRouter().models()}.onSuccess{models=it;c.notice="已取得各系列最新可用的一般文字模型"}.onFailure{c.notice="無法更新模型，現有選項仍可使用"};loading=false}}){Text(if(loading)"更新中…"else"更新最新模型清單")}
            }
            Text("按下分析才會呼叫 OpenRouter，費用依所選模型計算。金鑰不會交給網站；送出的資料會由 OpenRouter 與模型供應商處理。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    EngineVersionPanel(c)
}
@Composable private fun SyncPanel(c:BrowserController,store:BrowserStore){
    val sync=(c.context as MainActivity).bookmarkSync
    c.revision
    SheetTitle("Google 同步","把書籤、收藏進度與天眼設定帶到下一支手機。")
    Surface(modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Outlined.CloudDone,null,tint=MaterialTheme.colorScheme.primary)
            Text(sync.status,fontWeight=FontWeight.SemiBold)
            if(sync.details.isNotBlank())Text(sync.details,fontSize=11.sp,color=MaterialTheme.colorScheme.error,lineHeight=17.sp)
            if(sync.connected)Text(store.bookmarkStore.accountLabel,fontSize=13.sp)
            if(store.bookmarkStore.lastSync>0)Text("上次完成："+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(store.bookmarkStore.lastSync)),fontSize=12.sp)
        }
    }
    Text("書籤、資料夾、收藏及刪除紀錄會存入 Google Drive 的應用程式專用隱藏空間，不會取得你其他檔案的存取權。",fontSize=14.sp,lineHeight=23.sp)
    SettingsGroup("收藏與閱讀進度"){
        Text("收藏的名稱、目前閱讀網址、頁面標題、捲動位置與閱讀百分比會一起同步。請在每支裝置更新至支援收藏同步的版本。",fontSize=13.sp,lineHeight=21.sp)
        Text("同一收藏以最近儲存的完整紀錄為準；重新命名與刪除也會同步，不會強迫保留最高閱讀百分比。正在開啟的頁面不會被強制跳轉，下次從收藏開啟時使用同步後的位置。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var siteSync by remember{mutableStateOf(store.syncSiteSettings)}
    SettingsGroup("天眼網站設定"){
        Text("開啟後，網域、元件規則、自訂 CSS／JS／HTML 及網站行為設定會同步至你自己的 Google Drive。程式碼可能包含私人內容，請先確認沒有密碼或金鑰。",fontSize=13.sp,lineHeight=21.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("同步天眼網站設定",Modifier.weight(1f),fontSize=15.sp)
            Switch(siteSync,{value->store.syncSiteSettings=value;siteSync=value;if(sync.connected)sync.changed()},enabled=!sync.busy,modifier=Modifier.testTag("sync-site-settings"))
        }
        Text("同一網站以較新的完整設定為準；已清除的設定也會保留刪除狀態。其他裝置收到後，在下次載入網站時套用。暫時例外與憑證例外只留在本機。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("連結後，這支手機的書籤與收藏會與你選擇的 Google 帳戶合併。App 開啟時，以及修改書籤、儲存收藏進度、重新命名或刪除收藏後會自動同步；離線時先留在手機，下次連線再同步。",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Button(enabled=!sync.busy,onClick={sync.authorize(true)},modifier=Modifier.fillMaxWidth().height(50.dp)){
        if(sync.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
        Text(if(sync.busy)"處理中…"else if(sync.connected)"立即同步"else"使用 Google 帳戶連結")
    }
    if(sync.connected)TextButton(onClick={sync.disconnect()},Modifier.fillMaxWidth()){Text("停止同步並登出")}
    Text("登出會保留本機與雲端資料。本機同步資料綁定首次同步的 Google 帳戶，避免切換帳戶時混入別人的資料。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun UserAgentPanel(c:BrowserController){
    var mode by remember{mutableStateOf(c.store.userAgentMode)}
    var custom by remember{mutableStateOf(c.store.customUserAgent)}
    var error by remember{mutableStateOf("")}
    SheetTitle("瀏覽器識別","有些網站會依識別資訊，提供不同的版型。")
    listOf("chrome" to "Chrome 手機版（預設）","webview" to "原始 Android WebView","custom" to "自訂 User-Agent").forEach{(value,label)->
        Row(Modifier.fillMaxWidth().clickable{mode=value},verticalAlignment=Alignment.CenterVertically){RadioButton(mode==value,{mode=value});Text(label,fontSize=15.sp)}
    }
    if(mode=="custom")OutlinedTextField(custom,{custom=it},Modifier.fillMaxWidth().testTag("custom-user-agent"),label={Text("User-Agent")},minLines=3,maxLines=5,supportingText={Text("貼上完整的單行 User-Agent")})
    Text("儲存後會重新載入目前頁面；新開啟或重新整理的其他頁面也會使用這個設定。網站可能改用另一種版型，支援程度仍受目前瀏覽核心影響。",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    Button(onClick={runCatching{c.setUserAgent(mode,custom)}.onSuccess{c.sheet="";c.notice="已更新瀏覽器識別"}.onFailure{error=it.localizedMessage?:"無法儲存"}},Modifier.fillMaxWidth()){Text("儲存並重新載入")}
    Text("目前瀏覽核心：Android WebView ${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(c.context)?.versionName?:"未知"}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
