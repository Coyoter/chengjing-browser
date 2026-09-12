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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val dark=theme=="dark"||(theme=="system"&&isSystemInDarkTheme())
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    val focus=LocalFocusManager.current
    val activity=c.context as MainActivity
    val active=c.active
    c.revision
    var address by remember(active?.id,active?.url){mutableStateOf(active?.url.orEmpty())}
    var editingAddress by remember{mutableStateOf(false)}
    val cs=if(dark)Dark else Light
    SideEffect{WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightStatusBars=!dark;WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightNavigationBars=!dark}
    LaunchedEffect(c){
        snapshotFlow{c.notice}.filter{it.isNotEmpty()}.collectLatest{message->
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
        Surface(Modifier.fillMaxSize(),color=cs.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Column(Modifier.fillMaxSize()){
                    BrowserAddressBar(
                        address=address,onAddress={address=it},editing=editingAddress,onFocus={editingAddress=it},
                        loading=(active?.progress?:100)<100,secure=active?.url?.startsWith("https:")==true&&active.error.isEmpty()&&active.certificateWarning.isEmpty(),
                        certificateWarning=active?.certificateWarning?.isNotEmpty()==true,
                        blank=active?.url.isNullOrEmpty(),tabs=c.tabs.size,
                        onGo={c.navigate(address);editingAddress=false;focus.clearFocus()},
                        onSecurity={c.notice=when{active?.certificateWarning?.isNotEmpty()==true->active.certificateWarning;active?.error?.isNotEmpty()==true->active.error;active?.url?.startsWith("https:")==true->"HTTPS 加密連線 · ${c.domain}";c.domain.isEmpty()->"輸入網址或搜尋關鍵字";else->"HTTP 連線未加密 · ${c.domain}"}},
                        onReload={if((active?.progress?:100)<100)active?.web?.stopLoading()else c.reload()},
                        onTabs={focus.clearFocus();c.sheet="tabs"},
                    )
                    if((active?.progress?:100)<100)LinearProgressIndicator(progress={(active?.progress?:0)/100f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=Color.Transparent)
                    if(c.eye) {
                        Row(Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Visibility,null,tint=cs.primary,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                            Text(if(c.dirty)"${c.draft?.rules?.size?:0} 條規則 · 尚未儲存"else"天眼已開啟 · 點選網站元件",Modifier.weight(1f),fontSize=12.sp,color=cs.onPrimaryContainer)
                            if(c.dirty)TextButton(onClick={c.saveDraft()}){Text("儲存")}
                            Tool(Icons.Outlined.Close,"離開天眼"){c.stopEye()}
                        }
                    } else if(c.isException) {
                        Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Text("例外中 · 原始網站",Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={c.exception()}){Text("恢復規則")}}
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.url.isNullOrEmpty())Home(c,store)
                        else if(active?.error?.isNotEmpty()==true)Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CloudOff,null,Modifier.size(48.dp),tint=cs.primary);Spacer(Modifier.height(24.dp));Text("暫時連不上這個網站",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(12.dp));Text(active.error,color=cs.onSurfaceVariant);Spacer(Modifier.height(20.dp));Button(onClick={c.reload()}){Text("重新載入")}}
                        else active?.let{tab->key(tab.id){AndroidView(factory={(tab.web.parent as? android.view.ViewGroup)?.removeView(tab.web);tab.web},modifier=Modifier.fillMaxSize())}}
                    }
                    Surface(color=cs.surface){
                        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                            Tool(Icons.AutoMirrored.Outlined.ArrowBack,"上一頁",active?.canBack==true){c.stopEye();active?.web?.goBack()}
                            Tool(Icons.AutoMirrored.Outlined.ArrowForward,"下一頁",active?.canForward==true){c.stopEye();active?.web?.goForward()}
                            FilledTonalButton(onClick={focus.clearFocus();c.beginEye()},contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){
                                Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("天眼",fontWeight=FontWeight.SemiBold)
                                if(c.site.rules.isNotEmpty()){Spacer(Modifier.width(6.dp));Text("${c.site.rules.size}",fontSize=12.sp)}
                            }
                            Tool(if(c.isException)Icons.Outlined.Shield else Icons.Outlined.ShieldMoon,"切換網站例外",c.domain.isNotEmpty()){c.exception()}
                            Tool(Icons.Outlined.MoreHoriz,"瀏覽器選單"){c.sheet="menu"}
                        }
                    }
                }
                SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=70.dp))
            }
        }
        if(c.sheet.isNotEmpty()){
            ModalBottomSheet(onDismissRequest={c.sheet=""},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=cs.surface,dragHandle={BottomSheetDefaults.DragHandle()},modifier=Modifier.fillMaxWidth()){
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=24.dp,end=24.dp,bottom=28.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    when(c.sheet){
                        "menu"->{
                            SheetTitle("澄境瀏覽器","讓網頁回到你喜歡的樣子。")
                            MenuRow(Icons.Outlined.Add,"新增分頁"){c.newTab();c.sheet=""}
                            MenuRow(Icons.Outlined.BookmarkBorder,"書籤"){c.sheet="bookmarks"}
                            MenuRow(Icons.Outlined.FileDownload,"匯入 Chrome 書籤","選擇 Chrome 匯出的 HTML 檔"){c.sheet="";activity.importBookmarks()}
                            if(c.domain.isNotEmpty())MenuRow(Icons.Outlined.BookmarkAdd,"收藏／取消收藏這一頁"){store.bookmark(active!!.url,active.title);c.notice="書籤已更新";c.sheet=""}
                            MenuRow(Icons.Outlined.History,"瀏覽紀錄"){c.sheet="history"}
                            MenuRow(Icons.Outlined.ManageSearch,"尋找頁面文字"){c.sheet="find"}
                            if(c.domain.isNotEmpty())MenuRow(Icons.Outlined.Computer,if(active?.desktop==true)"切換手機版網站"else"切換電腦版網站"){
                                active?.let{it.desktop=!it.desktop;it.web.settings.userAgentString=if(it.desktop)it.web.settings.userAgentString.replace("; wv","").replace(" Mobile","").replace("Android", "X11; Linux x86_64")else android.webkit.WebSettings.getDefaultUserAgent(c.context);it.web.reload()};c.sheet=""
                            }
                            if(c.domain.isNotEmpty())MenuRow(Icons.Outlined.Visibility,"天眼設定"){c.sheet="eye"}
                            MenuRow(Icons.Outlined.Tune,"所有網域規則"){c.sheet="domains"}
                            MenuRow(Icons.Outlined.CloudSync,"Google 書籤同步"){c.sheet="sync"}
                            MenuRow(Icons.Outlined.Settings,"外觀與 AI 設定"){c.sheet="settings"}
                            MenuRow(Icons.Outlined.Science,"天眼練習場"){c.navigate("https://practice.chengjing.invalid/")}
                            if(active?.blockedUrl?.isNotEmpty()==true)MenuRow(Icons.Outlined.OpenInNew,"查看被攔下的跳轉"){c.sheet="blocked"}
                            Text("${BuildConfig.VERSION_NAME} · Android 自用版",fontSize=12.sp,color=cs.onSurfaceVariant)
                        }
                        "tabs"->{SheetTitle("分頁","${c.tabs.size} 個開啟中的頁面")
                            c.tabs.toList().forEach{tab->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{c.switchTab(tab.id)}.padding(vertical=12.dp)){Text(tab.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=if(tab.id==c.activeId)FontWeight.Bold else FontWeight.Normal);Text(Domains.scope(tab.url).ifEmpty{"澄境首頁"},fontSize=12.sp,color=cs.onSurfaceVariant)};Tool(Icons.Outlined.Close,"關閉 ${tab.title}"){c.closeTab(tab.id)}}}
                            Button(onClick={c.newTab();c.sheet=""},Modifier.fillMaxWidth()){Text("新增分頁")}
                        }
                        "eye"->EyePanel(c)
                        "selection"->SelectionPanel(c)
                        "rules"->RulePanel(c)
                        "code"->CodePanel(c)
                        "ai"->AiPanel(c,store)
                        "settings"->SettingsPanel(store,theme,{theme=it;store.theme=it},c)
                        "inventory"->InventoryPanel(c)
                        "bookmarks"->BookmarksPanel(c,store)
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
                        "blocked"->{SheetTitle("被攔下的跳轉","確認網址後，可以自行開啟。")
                            Text(active?.blockedUrl.orEmpty(),fontSize=14.sp);Button(onClick={c.navigate(active?.blockedUrl.orEmpty())}){Text("開啟此網址")}
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
        Text("把目光，\n留給喜歡的事。",fontSize=35.sp,lineHeight=47.sp,fontWeight=FontWeight.Light,letterSpacing=(-1).sp)
        Spacer(Modifier.height(18.dp))
        Text("照常瀏覽。遇到干擾時，\n打開天眼，讓畫面回到你的步調。",fontSize=15.sp,lineHeight=25.sp,color=cs.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Surface(shape=RoundedCornerShape(20.dp),color=cs.surface){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.TouchApp,null,tint=cs.primary);Spacer(Modifier.width(10.dp));Text("先試一次天眼",fontWeight=FontWeight.SemiBold)}
            Text("點中元件、預覽移除、儲存。\n下次來到同一個網域，畫面依然清爽。",fontSize=14.sp,lineHeight=23.sp,color=cs.onSurfaceVariant)
            TextButton(onClick={c.navigate("https://practice.chengjing.invalid/")},contentPadding=PaddingValues(0.dp)){Text("進入練習場");Spacer(Modifier.width(10.dp));Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(18.dp))}
        }}
        Spacer(Modifier.height(26.dp))
        Text("快速前往",fontSize=12.sp,color=cs.onSurfaceVariant);Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            AssistChip(onClick={c.navigate("https://www.google.com")},label={Text("Google")},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(16.dp))})
            AssistChip(onClick={c.navigate("https://zh.wikipedia.org")},label={Text("維基百科")},leadingIcon={Icon(Icons.Outlined.Language,null,Modifier.size(16.dp))})
        }
        store.bookmarks().take(4).forEach{(url,title)->Text(title,Modifier.fillMaxWidth().clickable{c.navigate(url)}.padding(vertical=12.dp),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp)}
    }
}
@Composable private fun Tool(icon:ImageVector,label:String,enabled:Boolean=true,onClick:()->Unit){IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)){Icon(icon,label,Modifier.size(22.dp))}}
@Composable private fun SheetTitle(title:String,subtitle:String=""){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontSize=24.sp,fontWeight=FontWeight.SemiBold);if(subtitle.isNotEmpty())Text(subtitle,fontSize=14.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun MenuRow(icon:ImageVector,title:String,subtitle:String="",action:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=action).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,fontSize=16.sp);if(subtitle.isNotEmpty())Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SwitchRow(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(end=12.dp)){Text(title,fontSize=16.sp);Text(description,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}}

@Composable private fun EyePanel(c:BrowserController){
    SheetTitle("天眼",c.domain)
    Text("看見網站結構，把干擾留在畫面之外。",color=MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick={if(c.eye)c.sheet=""else c.beginEye()},enabled=!c.isException,modifier=Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(if(c.eye)"繼續選取元件"else"開啟天眼・選取元件")}
    if(c.eye && c.dirty)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={c.undoDraft()},Modifier.weight(1f)){Text("撤回上一個")};Button(onClick={c.saveDraft();c.sheet=""},Modifier.weight(1f)){Text("儲存規則")}}
    MenuRow(Icons.Outlined.Layers,"結構清單","查看浮動、隱藏與內嵌元件"){if(!c.eye)c.beginEye();if(c.eye)c.sheet="inventory"}
    MenuRow(Icons.Outlined.Tune,"已儲存的移除規則","${c.site.rules.size} 條 · 適用整個網域及子網域"){c.sheet="rules"}
    MenuRow(Icons.Outlined.AutoAwesome,"請 AI 幫忙","描述哪裡卡住，檢查並修正移除方式"){c.sheet="ai"}
    MenuRow(Icons.Outlined.Code,"自訂 CSS / JavaScript"){c.sheet="code"}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    SwitchRow("防止跳轉與彈窗","攔截沒有點擊的跳轉及新視窗。登入流程受影響時可關閉。",c.site.guard){c.saveSite(c.site.copy(guard=it));c.notice="網域防護設定已儲存"}
    SwitchRow("暫時顯示原始網站","暫停此網域所有自訂設定，直到關閉 App。",c.isException){c.exception()}
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
@Composable private fun CodePanel(c:BrowserController){
    var css by remember{mutableStateOf(c.site.css)};var js by remember{mutableStateOf(c.site.js)}
    SheetTitle("自訂程式碼",c.domain)
    Text("只會在這個網域及子網域執行。程式碼可以讀取與修改頁面，請只貼入你信任的內容。例外模式會停止套用並重新載入。",fontSize=13.sp,lineHeight=21.sp)
    OutlinedTextField(css,{css=it},label={Text("CSS 樣式")},placeholder={Text(".annoying-banner { display: none !important; }")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=7)
    OutlinedTextField(js,{js=it},label={Text("JavaScript")},placeholder={Text("// 網頁載入後執行一次")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=7)
    Button(onClick={runCatching{c.saveSite(c.site.copy(css=css,js=js),reload=true)}.onSuccess{c.sheet="";c.notice="已儲存並重新載入"}.onFailure{c.notice="儲存失敗，程式碼請限制在 50,000 字內"}},Modifier.fillMaxWidth()){Text("儲存並套用")}
}
@Composable private fun InventoryPanel(c:BrowserController){
    var elements by remember{mutableStateOf<List<JSONObject>>(emptyList())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit){val raw=c.js("window.__chengjingEye?.inventory()");elements=runCatching{val a=JSONArray(raw);(0 until a.length()).map{a.getJSONObject(it)}}.getOrDefault(emptyList());loading=false}
    SheetTitle("結構清單","包括原本看不見的元件。最多顯示 80 個浮動、隱藏及內嵌元件。")
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(!loading&&elements.isEmpty())Text("目前沒有找到這類元件；仍可直接點選網頁中的一般元件。")
    elements.forEach{j->MenuRow(if(j.optBoolean("hidden"))Icons.Outlined.VisibilityOff else Icons.Outlined.Layers,j.optString("label"),if(j.optBoolean("hidden"))"原本隱藏"else if(j.optBoolean("fixed"))"浮動／固定元件"else"內嵌頁面"){c.chooseSelector(j.getString("selector"))}}
}
@Composable private fun SettingsPanel(store:BrowserStore,theme:String,onTheme:(String)->Unit,c:BrowserController){
    val scope=rememberCoroutineScope();var key by remember{mutableStateOf("")};var hasKey by remember{mutableStateOf(store.hasKey())};var model by remember{mutableStateOf(store.model)};var models by remember{mutableStateOf(OpenRouter.defaults)};var loading by remember{mutableStateOf(false)}
    SheetTitle("依你的習慣", "外觀與 AI 設定")
    Text("瀏覽器外觀",fontWeight=FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("light" to "淺色","dark" to "深色","system" to "系統").forEach{(id,label)->FilterChip(selected=theme==id,onClick={onTheme(id)},label={Text(label)},leadingIcon={Icon(when(id){"light"->Icons.Outlined.LightMode;"dark"->Icons.Outlined.DarkMode;else->Icons.Outlined.BrightnessAuto},null,Modifier.size(16.dp))})}}
    Text("跟隨系統會依手機的深色設定切換；不強制改寫網站自身配色。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Text("OpenRouter",fontWeight=FontWeight.SemiBold)
    Text(if(hasKey)"API Key 已加密儲存在手機。"else"加入你的 API Key，讓 AI 協助修正天眼規則。",fontSize=13.sp)
    OutlinedTextField(key,{key=it},label={Text(if(hasKey)"輸入新的 API Key 以替換"else"API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    if(key.isNotEmpty())Button(onClick={runCatching{store.saveKey(key)}.onSuccess{key="";hasKey=true;c.notice="API Key 已加密儲存"}.onFailure{c.notice="金鑰儲存失敗：${it.localizedMessage}"}},Modifier.fillMaxWidth()){Text("儲存 API Key")}
    if(hasKey)TextButton(onClick={store.saveKey("");hasKey=false;c.notice="API Key 已移除"}){Text("移除已儲存的 API Key")}
    Text("選擇模型",fontWeight=FontWeight.SemiBold)
    models.forEach{id->Row(Modifier.fillMaxWidth().clickable{model=id;store.model=id},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=model==id,onClick={model=id;store.model=id});Text(id,fontSize=12.sp)}}
    OutlinedTextField(model,{model=it;store.model=it},label={Text("或自行輸入模型名稱")},singleLine=true,modifier=Modifier.fillMaxWidth())
    TextButton(enabled=!loading,onClick={loading=true;scope.launch{runCatching{OpenRouter().models()}.onSuccess{models=it;c.notice="已取得各系列最新可用的一般文字模型"}.onFailure{c.notice="無法更新模型，現有選項仍可使用"};loading=false}}){Text(if(loading)"更新中…"else"更新最新模型清單")}
    Text("按下分析才會呼叫 OpenRouter，費用依你選擇的模型計算。金鑰不會交給網站；網頁資料會由 OpenRouter 與模型供應商處理。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun AiPanel(c:BrowserController,store:BrowserStore){
    val scope=rememberCoroutineScope()
    var problem by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var proposal by remember{mutableStateOf<AiProposal?>(null)};var error by remember{mutableStateOf("")};var previewing by remember{mutableStateOf(false)}
    val original=remember{c.draft?:c.site};val domain=remember{c.domain};val tabId=remember{c.activeId}
    SheetTitle("讓天眼再看仔細一點",domain)
    Text("例如：蓋版廣告消失了，但網頁不能捲動；或是移除後，連文章也一起不見了。",fontSize=14.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(problem,{problem=it},label={Text("你遇到了什麼問題？")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5)
    Text("將傳送：網域、元件標籤／類別／位置、目前規則與上述問題。不含文章內文、表單值、Cookie 或網址參數。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(!store.hasKey())Button(onClick={c.sheet="settings"},Modifier.fillMaxWidth()){Text("先加入 OpenRouter API Key")}
    else Button(enabled=!busy&&problem.isNotBlank(),onClick={busy=true;error="";proposal=null;scope.launch{
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

@Composable private fun BookmarksPanel(c:BrowserController,store:BrowserStore){
    c.revision
    val activity=c.context as MainActivity
    var query by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<Bookmark?>(null)}
    var editTitle by remember{mutableStateOf("")};var editFolder by remember{mutableStateOf("")}
    SheetTitle("書籤","好內容，留著下次再看。")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={activity.importBookmarks()},Modifier.weight(1f)){Text("從 Chrome 匯入")}
        OutlinedButton(onClick={activity.exportBookmarks()},Modifier.weight(1f)){Text("匯出")}
    }
    Text("先在電腦版 Chrome 的書籤管理員匯出 HTML，再把檔案存到手機並選取。也支援 Google 匯出服務產生的書籤 HTML。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    MenuRow(Icons.Outlined.CloudSync,"Google 書籤同步",activity.bookmarkSync.status){c.sheet="sync"}
    val all=store.bookmarkStore.visible()
    if(all.isNotEmpty())OutlinedTextField(query,{query=it},label={Text("搜尋書籤或資料夾")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    val rows=all.filter{"${it.title} ${it.url} ${it.folder}".contains(query,true)}
    if(rows.isEmpty())Text(if(all.isEmpty())"還沒有書籤。可匯入 Chrome 書籤，或從選單收藏目前頁面。"else"找不到符合的書籤。",fontSize=14.sp)
    rows.groupBy{it.folder}.forEach{(folder,items)->
        Text(folder.ifEmpty{"未分類"},fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        items.forEach{row->Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f).clickable{c.navigate(row.url)}.padding(vertical=10.dp)){Text(row.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp);Text(Domains.scope(row.url),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Tool(Icons.Outlined.Edit,"編輯 ${row.title}"){editing=row;editTitle=row.title;editFolder=row.folder}
            Tool(Icons.Outlined.Close,"刪除 ${row.title}"){store.bookmarkStore.remove(row.id);c.revision++}
        }}
    }
    editing?.let{entry->AlertDialog(onDismissRequest={editing=null},title={Text("編輯書籤")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(editTitle,{editTitle=it},label={Text("名稱")});OutlinedTextField(editFolder,{editFolder=it},label={Text("資料夾")},placeholder={Text("閱讀 / 科技")})}},confirmButton={TextButton(onClick={store.bookmarkStore.edit(entry.id,editTitle,editFolder);editing=null;c.revision++}){Text("儲存")}},dismissButton={TextButton(onClick={editing=null}){Text("取消")}})}
}
@Composable private fun SyncPanel(c:BrowserController,store:BrowserStore){
    val sync=(c.context as MainActivity).bookmarkSync
    c.revision
    SheetTitle("Google 書籤同步","把書籤帶到下一支手機。")
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){
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
