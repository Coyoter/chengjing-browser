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
        @Suppress("DEPRECATION")
        val taskIcon=if(android.os.Build.VERSION.SDK_INT>=33)android.app.ActivityManager.TaskDescription.Builder().setLabel("澄境瀏覽器").setIcon(R.mipmap.ic_launcher).build()
            else android.app.ActivityManager.TaskDescription("澄境瀏覽器",R.mipmap.ic_launcher)
        setTaskDescription(taskIcon)
        store=BrowserStore(this);controller=BrowserController(this,store)
        bookmarkSync=BookmarkSync(this,store.bookmarkStore)
        bookmarkSync.launchConsent={consent.launch(it)}
        controller.chooseFiles={files.launch(it)}
        val incoming=intent?.dataString
        if(incoming?.startsWith("https://")==true||incoming?.startsWith("http://")==true)controller.newTab(incoming) else controller.restoreTabs()
        setContent { BrowserApp(controller,store) }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);intent.dataString?.let{if(it.startsWith("https://")||it.startsWith("http://"))controller.newTab(it)}}
    override fun onPause(){super.onPause();android.webkit.CookieManager.getInstance().flush();controller.persistTabs()}
    override fun onResume(){super.onResume();if(::bookmarkSync.isInitialized)bookmarkSync.resume()}
    override fun onDestroy(){bookmarkSync.destroy();controller.destroy();super.onDestroy()}
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
    val activity=c.context as MainActivity
    val active=c.active
    c.revision
    val pageFavorite=c.favorites.forPage(active?.url.orEmpty())
    var address by remember(active?.id,active?.url){mutableStateOf(TextFieldValue(active?.url.orEmpty()))}
    var editingAddress by remember{mutableStateOf(false)}
    val cs=if(dark)Dark else Light
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
                        blank=active?.url.isNullOrEmpty(),tabs=c.tabs.size,
                        onGo={c.navigate(address.text);editingAddress=false;focus.clearFocus()},
                        onSecurity={focus.clearFocus();c.sheet="connection"},
                        onReload={if((active?.progress?:100)<100){active?.web?.stopLoading();active?.refreshContainer?.isRefreshing=false}else c.reload()},
                        onTabs={focus.clearFocus();c.sheet="tabs"},
                        onNewTab={focus.clearFocus();editingAddress=false;c.newTab()},
                    )
        }
        val controlsBar:@Composable ()->Unit = {
            Surface(color=cs.surface){
                        Row(Modifier.fillMaxWidth().testTag("browser-controls").height(64.dp).padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                            Tool(Icons.AutoMirrored.Outlined.ArrowBack,"上一頁",active?.canBack==true){c.stopEye();active?.web?.goBack()}
                            Tool(Icons.AutoMirrored.Outlined.ArrowForward,"下一頁",active?.canForward==true){c.stopEye();active?.web?.goForward()}
                            FilledTonalButton(onClick={focus.clearFocus();c.beginEye()},contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){
                                Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("天眼",fontWeight=FontWeight.SemiBold)
                                if(c.site.rules.isNotEmpty()){Spacer(Modifier.width(6.dp));Text("${c.site.rules.size}",fontSize=12.sp)}
                            }
                            Tool(if(pageFavorite!=null)Icons.Filled.Star else Icons.Outlined.StarOutline,if(active?.favoriteId!=null||pageFavorite!=null)"更新收藏進度"else"快速收藏",c.domain.isNotEmpty(),modifier=Modifier.testTag("quick-favorite").semantics{stateDescription=if(pageFavorite!=null)"已收藏"else"尚未收藏"}){
                                focus.clearFocus()
                                scope.launch{if(c.saveFavorite()!=null)c.notice="已保存收藏與閱讀位置"}
                            }
                            Box(Modifier.size(48.dp)){
                                Tool(Icons.Outlined.MoreHoriz,"瀏覽器選單"){focus.clearFocus();active?.blockedUnread=false;c.sheet="menu"}
                                if(active?.blockedUnread==true)Box(Modifier.size(5.dp).testTag("blocking-indicator").align(Alignment.TopEnd).offset(x=(-8).dp,y=8.dp).background(cs.primary,CircleShape))
                            }
                        }
                    }
        }
        Surface(Modifier.fillMaxSize(),color=cs.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Column(Modifier.fillMaxSize()){
                    if(addressAtBottom)controlsBar()else addressBar()
                    if(!addressAtBottom&&(active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                    if(c.eye) {
                        Row(Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Visibility,null,tint=cs.primary,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                            Text(if(c.dirty)"${c.draft?.rules?.size?:0} 條規則 · 尚未儲存"else"天眼已開啟 · 點選網站元件",Modifier.weight(1f),fontSize=12.sp,color=cs.onPrimaryContainer)
                            if(c.dirty)TextButton(onClick={c.saveDraft()}){Text("儲存")}
                            Tool(Icons.Outlined.Close,"離開天眼"){c.stopEye()}
                        }
                        Row(Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextButton(onClick={codeFocus="css";c.sheet="code"},modifier=Modifier.weight(1f).height(48.dp)){
                                Icon(Icons.Outlined.Palette,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("新增 CSS")
                            }
                            TextButton(onClick={codeFocus="js";c.sheet="code"},modifier=Modifier.weight(1f).height(48.dp)){
                                Icon(Icons.Outlined.Code,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("新增 JS")
                            }
                        }
                    } else if(c.isException) {
                        Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Text("例外中 · 原始網站",Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={c.exception()}){Text("恢復規則")}}
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.url.isNullOrEmpty())Home(c,store)
                        else if(active?.error?.isNotEmpty()==true)Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CloudOff,null,Modifier.size(48.dp),tint=cs.primary);Spacer(Modifier.height(24.dp));Text("暫時連不上這個網站",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(12.dp));Text(active.error,color=cs.onSurfaceVariant);Spacer(Modifier.height(20.dp));Button(onClick={c.reload()}){Text("重新載入")}}
                        else active?.let{tab->key(tab.id){AndroidView(factory={(tab.refreshContainer.parent as? android.view.ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer},update={it.setColorSchemeColors(cs.primary.toArgb());it.setProgressBackgroundColorSchemeColor(cs.surface.toArgb())},modifier=Modifier.fillMaxSize().testTag("web-content"))}}
                    }
                    if(addressAtBottom){
                        if((active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                        addressBar()
                    }else controlsBar()
                }
                SnackbarHost(snackbar,Modifier.testTag("browser-notices").align(Alignment.BottomCenter).padding(bottom=70.dp))
            }
        }
        if(c.sheet in setOf("bookmarks","favorites"))LibraryScreen(c)
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites")){
            BrowserPanel(c){
                    PanelHeader(c){c.sheet=panelTrail.dropLast(1).lastOrNull()?:panelParent(c.sheet)}
                    if(c.sheet=="settings")SettingsCategories(settingsCategory){settingsCategory=it}
                    key(c.sheet,if(c.sheet=="settings")settingsCategory else ""){
                        Column(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("panel-content").verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=4.dp,bottom=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(20.dp)){
                    when(c.sheet){
                        "menu"->BrowserMainMenu(c)
                        "connection"->ConnectionPanel(c)
                        "privacy"->PrivacyPanel(c)
                        "tabs"->{SheetTitle("分頁","${c.tabs.size} 個開啟中的頁面")
                            c.tabs.toList().forEach{tab->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{c.switchTab(tab.id)}.padding(vertical=12.dp)){Text(tab.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=if(tab.id==c.activeId)FontWeight.Bold else FontWeight.Normal);Text(Domains.scope(tab.url).ifEmpty{"澄境首頁"},fontSize=12.sp,color=cs.onSurfaceVariant)};Tool(Icons.Outlined.Close,"關閉 ${tab.title}"){c.closeTab(tab.id)}}}
                            Button(onClick={c.newTab();c.sheet=""},Modifier.fillMaxWidth()){Text("新增分頁")}
                        }
                        "eye"->EyePanel(c)
                        "selection"->SelectionPanel(c)
                        "rules"->RulePanel(c)
                        "code"->CodePanel(c,codeFocus)
                        "ai"->AiPanel(c,store){settingsCategory="ai";c.sheet="settings"}
                        "settings"->SettingsPanel(settingsCategory,store,theme,{theme=it;store.theme=it},c,addressAtBottom,{addressAtBottom=it;store.addressAtBottom=it})
                        "user-agent"->UserAgentPanel(c)
                        "inventory"->InventoryPanel(c)
                        "sync"->SyncPanel(c,store)
                        "history"->{SheetTitle("瀏覽紀錄","最近 250 個頁面，只留在這支手機。")
                            val entries=store.history()
                            if(entries.isEmpty())Text("這裡還沒有內容。",color=cs.onSurfaceVariant)
                            entries.forEach{(url,title)->Column(Modifier.fillMaxWidth().clickable{c.navigate(url)}.padding(vertical=10.dp)){Text(title,maxLines=1,overflow=TextOverflow.Ellipsis);Text(Domains.scope(url),fontSize=12.sp,color=cs.onSurfaceVariant)}}
                            if(entries.isNotEmpty())TextButton(onClick={store.clearHistory();c.sheet=""}){Text("清除瀏覽紀錄")}
                        }
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
        c.fullScreenView?.let{view->androidx.compose.ui.window.Dialog(onDismissRequest={c.exitFullscreen()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){AndroidView(factory={(view.parent as? android.view.ViewGroup)?.removeView(view);view},modifier=Modifier.fillMaxSize())}}
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
            recentFavorites.forEach{favorite->Row(Modifier.fillMaxWidth().clickable{c.openFavorite(favorite)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.StarOutline,null,Modifier.size(18.dp),tint=cs.primary);Spacer(Modifier.width(10.dp));Text(favorite.title,Modifier.weight(1f),fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
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
    MenuGroup("元件與規則"){
        MenuRow(Icons.Outlined.Layers,"結構清單","查看浮動、隱藏與內嵌元件"){if(!c.eye)c.beginEye();if(c.eye)c.sheet="inventory"}
        MenuRow(Icons.Outlined.Tune,"已儲存的移除規則","${c.site.rules.size} 條 · 適用此網域與子網域"){c.sheet="rules"}
        MenuRow(Icons.Outlined.AutoAwesome,"請 AI 幫忙","檢查並修正移除方式"){c.sheet="ai"}
        MenuRow(Icons.Outlined.Code,"自訂 CSS / JavaScript"){c.sheet="code"}
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
    SheetTitle("選中一個元件",selection.label)
    Text("${selection.width} × ${selection.height} · 目前符合 ${selection.count} 個元件",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(selection.frame)Text("這是內嵌頁面。移除會處理整個框架；跨網域的內部結構無法在此逐一選取。",fontSize=13.sp)
    if(selection.count>1)Text("這條規則會一起移除 ${selection.count} 個相同結構的元件。",color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    Text("先預覽，再決定是否儲存。內容換掉，只要結構仍符合，規則會繼續套用。",fontSize=14.sp,lineHeight=22.sp)
    Button(onClick={scope.launch{c.removeSelected()}},Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Outlined.VisibilityOff,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("預覽移除")}
    if(selection.canParent)OutlinedButton(onClick={c.parentSelection()},Modifier.fillMaxWidth()){Text("選取外面一層")}
    TextButton(onClick={c.sheet="ai"},Modifier.fillMaxWidth()){Text("這樣移除對嗎？請 AI 檢查")}
    TextButton(onClick={c.selection=null;c.sheet=""},Modifier.fillMaxWidth()){Text("改選其他元件")}
}
@Composable private fun RulePanel(c:BrowserController){
    val site=c.site
    SheetTitle("移除規則",c.domain)
    if(site.rules.isEmpty())Text("目前沒有移除規則。開啟天眼，點選不想看到的元件。")
    site.rules.forEach{rule->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(rule.label,fontSize=14.sp);Text(rule.selector,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Tool(Icons.AutoMirrored.Outlined.Undo,"恢復 ${rule.label}"){c.saveSite(c.site.copy(rules=c.site.rules.filterNot{it.selector==rule.selector}));c.notice="已恢復這個元件"}}}
    SwitchRow("恢復頁面捲動","移除蓋版後仍無法捲動時，才開啟。",site.unlockScroll){c.saveSite(site.copy(unlockScroll=it))}
    Text("規則保存在本機。網域的結構如果改版，可以重新選取或請 AI 調整。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun CodePanel(c:BrowserController,initialFocus:String){
    val original=remember{c.draft?:c.site};val tabId=remember{c.activeId};val includeDraft=remember{c.dirty}
    var css by remember{mutableStateOf(original.css)};var js by remember{mutableStateOf(original.js)};var error by remember{mutableStateOf("")}
    val cssFocus=remember{FocusRequester()};val jsFocus=remember{FocusRequester()}
    LaunchedEffect(Unit){when(initialFocus){"css"->cssFocus.requestFocus();"js"->jsFocus.requestFocus()}}
    SheetTitle("自訂程式碼",original.domain)
    Text("程式碼會套用到此網域及子網域。JavaScript 可讀取頁面並發出網路請求，請只使用信任的內容。例外模式會暫停套用。",fontSize=13.sp,lineHeight=21.sp)
    if(includeDraft)Text("儲存時會一併保留目前天眼預覽的元件修改。",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
    OutlinedTextField(css,{css=it;error=""},label={Text("CSS 樣式")},placeholder={Text("main { line-height: 1.8; }")},modifier=Modifier.fillMaxWidth().testTag("custom-css-input").focusRequester(cssFocus),minLines=4,maxLines=7)
    OutlinedTextField(js,{js=it;error=""},label={Text("JavaScript")},placeholder={Text("// 新增或調整頁面內容；每次載入後執行一次")},modifier=Modifier.fillMaxWidth().testTag("custom-js-input").focusRequester(jsFocus),minLines=4,maxLines=7)
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    Button(onClick={
        if(c.activeId!=tabId||c.domain!=original.domain){error="頁面已變更，請關閉後重新開啟編輯器"}
        else runCatching{c.saveSite(original.copy(css=css,js=js),reload=true)}.onSuccess{c.sheet="";c.notice="已儲存並重新載入"}.onFailure{error="儲存失敗：${it.localizedMessage}"}
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
                MenuRow(Icons.Outlined.CloudSync,"Google 書籤同步"){c.sheet="sync"}
                MenuRow(Icons.Outlined.Folder,"書籤資料夾"){c.sheet="bookmarks"}
                MenuRow(Icons.Outlined.History,"瀏覽紀錄"){c.sheet="history"}
            }
            MenuGroup("使用說明"){
                MenuRow(Icons.Outlined.PrivacyTip,"隱私與資料","資料用途、保存與刪除方式"){c.sheet="privacy"}
                MenuRow(Icons.Outlined.Science,"天眼練習場","練習調整元件、新增 CSS 與 JS"){c.navigate("https://practice.chengjing.invalid/")}
            }
            Text("澄境瀏覽器 ${BuildConfig.VERSION_NAME} · Android 版",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "ai"->{
            SettingsGroup("OpenRouter 連線"){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(if(hasKey)Icons.Outlined.VerifiedUser else Icons.Outlined.Key,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text(if(hasKey)"金鑰已儲存"else"連結你的 AI",fontSize=15.sp,fontWeight=FontWeight.Medium);Text(if(hasKey)"已加密保存在這支手機"else"讓 AI 協助檢查天眼規則",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                OutlinedTextField(key,{key=it},label={Text(if(hasKey)"輸入新的 API Key 以替換"else"API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
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
    EngineVersionPanel(c)
}
@Composable private fun AiPanel(c:BrowserController,store:BrowserStore,onSettings:()->Unit){
    val scope=rememberCoroutineScope()
    var problem by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var proposal by remember{mutableStateOf<AiProposal?>(null)};var error by remember{mutableStateOf("")};var previewing by remember{mutableStateOf(false)};var consent by remember{mutableStateOf(false)}
    val original=remember{c.draft?:c.site};val domain=remember{c.domain};val tabId=remember{c.activeId}
    SheetTitle("讓天眼再看仔細一點",domain)
    Text("例如：調整浮動面板後，網頁不能捲動；或是修改後，文章版面也受到影響。",fontSize=14.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(problem,{problem=it},label={Text("你遇到了什麼問題？")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5)
    Text("按下分析會將網域、元件標籤／類別／位置、目前規則與你輸入的問題，傳送給 OpenRouter 與所選模型供應商，以檢查天眼設定。不主動擷取文章內文、表單值、Cookie 或網址參數；請勿在問題中填入密碼或私人資料。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it});Text("我同意傳送上述資料供本次分析",fontSize=13.sp)}
    if(!store.hasKey())Button(onClick=onSettings,Modifier.fillMaxWidth()){Text("先加入 OpenRouter API Key")}
    else Button(enabled=!busy&&consent&&problem.isNotBlank(),onClick={busy=true;error="";proposal=null;scope.launch{
        runCatching{
            val raw=c.js("window.__chengjingEye?.snapshot()")
            val structure=if(raw=="null")error("網頁尚未準備好")else JSONArray("[$raw]").getString(0)
            OpenRouter().suggest(store.readKey(),store.model,problem,structure,original)
        }.onSuccess{if(c.activeId==tabId&&c.domain==domain)proposal=it else error="頁面已變更，請重新分析"}.onFailure{error=it.localizedMessage?:"AI 分析未完成，規則未變更"};busy=false
    }},modifier=Modifier.fillMaxWidth()){if(busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))};Text(if(busy)"正在檢查結構…"else"傳送結構並分析")}
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    proposal?.let{p->
        HorizontalDivider();Text("AI 的判斷",fontWeight=FontWeight.SemiBold);Text(p.explanation,fontSize=14.sp,lineHeight=22.sp)
        Text("新增 ${p.added.size} 條 · 恢復 ${p.removed.size} 條${if(p.result.unlockScroll)" · 恢復捲動"else""}",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
        p.added.forEach{Text("移除：$it",fontSize=11.sp)};p.removed.forEach{Text("恢復：$it",fontSize=11.sp)}
        if(!previewing)Button(onClick={scope.launch{
            if(c.activeId!=tabId||c.domain!=domain){error="頁面已變更，請重新分析";return@launch}
            val validation=c.validateSelectors(p.added)
            if(validation!=null)error=validation else {c.previewSite(p.result);previewing=true;c.eye=true;c.active?.web?.evaluateJavascript("window.__chengjingEye?.enable(false)",null);c.notice="已套用 AI 預覽；確認頁面後可儲存或取消";c.sheet=""}
        }},Modifier.fillMaxWidth()){Text("一鍵預覽 AI 修正")}
        Text("AI 建議會先在目前頁面預覽。確認後按天眼列的「儲存」；不合適就按 × 取消。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SyncPanel(c:BrowserController,store:BrowserStore){
    val sync=(c.context as MainActivity).bookmarkSync
    c.revision
    SheetTitle("Google 書籤同步","把書籤帶到下一支手機。")
    Surface(modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Outlined.CloudDone,null,tint=MaterialTheme.colorScheme.primary)
            Text(sync.status,fontWeight=FontWeight.SemiBold)
            if(sync.connected)Text(store.bookmarkStore.accountLabel,fontSize=13.sp)
            if(store.bookmarkStore.lastSync>0)Text("上次完成："+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(store.bookmarkStore.lastSync)),fontSize=12.sp)
        }
    }
    Text("只同步書籤、資料夾與刪除紀錄。使用 Google Drive 的應用程式專用隱藏空間，不會取得你其他檔案的存取權。",fontSize=14.sp,lineHeight=23.sp)
    Text("連結後，這支手機的書籤會與你選擇的 Google 帳戶合併。App 開啟時以及修改書籤後自動同步；離線時先留在手機，下次連線再同步。",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Button(enabled=!sync.busy,onClick={sync.authorize(true)},modifier=Modifier.fillMaxWidth().height(50.dp)){
        if(sync.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
        Text(if(sync.busy)"處理中…"else if(sync.connected)"立即同步"else"使用 Google 帳戶連結")
    }
    if(sync.connected)TextButton(onClick={sync.disconnect()},Modifier.fillMaxWidth()){Text("停止同步並登出")}
    Text("登出會保留本機與雲端書籤。此版本將本機書籤綁定首次同步的 Google 帳戶，避免切換帳戶時混入別人的資料。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
