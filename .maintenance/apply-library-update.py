"""Apply reviewed, exact-match edits on a dedicated feature branch, then remove this helper."""
from pathlib import Path
import subprocess

ROOT=Path.cwd()
K=ROOT/'app/src/main/java/tw/techtarian/browser'
BASE='553dd88faeafc6c635b4cc00891e0daa0bb5d0c0'
paths=[K/x for x in ['BrowserController.kt','BrowserData.kt','MainActivity.kt','BrowserMenus.kt','BrowserPageActions.kt']]+[ROOT/'app/build.gradle.kts',ROOT/'README.md',ROOT/'app/src/main/assets/privacy.txt']
for p in paths:
    expected=subprocess.check_output(['git','show',f'{BASE}:{p.relative_to(ROOT)}'])
    assert p.read_bytes()==expected, f'Refusing to overwrite concurrent changes: {p}'
original_controls=(K/'BrowserControls.kt').read_bytes()
def edit(name,before,after):
    p=K/name
    s=p.read_text()
    assert s.count(before)==1,(name,before[:100],s.count(before))
    p.write_text(s.replace(before,after))

edit('BrowserController.kt','class BrowserTab(val id: Int, val web: SelectionWebView) {','class BrowserTab(val id: Int, val web: SelectionWebView, val incognito:Boolean=false) {\n    var preview by mutableStateOf<Bitmap?>(null)\n    var pageIcon by mutableStateOf<Bitmap?>(null)')
edit('BrowserController.kt','    private var nextId = 1','    var tabCollection by mutableStateOf(false)\n    internal val privateSession by lazy { IncognitoSession { notice=it } }\n    val privateMode:Boolean get()=active?.incognito==true\n    val visibleTabCount:Int get()=tabs.count{it.incognito==privateMode}\n    private var nextId = 1')
edit('BrowserController.kt','    fun persistTabs() = store.saveTabs(tabs.map { it.url },tabs.map{it.favoriteId})','''    fun persistTabs() {
        val normal=tabs.filterNot{it.incognito}
        store.saveTabs(normal.map{it.url},normal.map{it.favoriteId})
    }
    fun showTabs(){capturePreview(active);tabCollection=privateMode;sheet="tabs"}
    internal fun capturePreview(tab:BrowserTab?){
        if(tab==null||tab.incognito||tab.url.isBlank()||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return
        runCatching{
            val image=Bitmap.createBitmap(360,234,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(image)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.scale(360f/tab.web.width,360f/tab.web.width)
            tab.web.draw(canvas)
            tab.preview=image
        }
    }
    @android.annotation.SuppressLint("RequiresFeature")
    internal fun cookiesFor(tab:BrowserTab,url:String):String? =
        if(tab.incognito)androidx.webkit.WebViewCompat.getProfile(tab.web).cookieManager.getCookie(url)
        else CookieManager.getInstance().getCookie(url)
''')
edit('BrowserController.kt','    fun restoreTabs() {\n        val saved','    fun restoreTabs() {\n        privateSession.cleanupStaleProfiles()\n        val saved')
edit('BrowserController.kt','    fun newTab(url: String = ""): BrowserTab? {','    fun newTab(url: String = "",incognito:Boolean=privateMode): BrowserTab? {')
edit('BrowserController.kt','        stopEye()\n        val web=SelectionWebView(context)','''        if(incognito&&!privateSession.supported){notice="請先更新 Android System WebView，才能使用隔離的無痕分頁";return null}
        capturePreview(active)
        stopEye()
        val web=SelectionWebView(context)
        if(incognito){
            try{privateSession.attach(web)}catch(_:Exception){web.destroy();notice="無法建立隔離的無痕環境，沒有開啟一般分頁代替";return null}
            web.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            web.isSaveEnabled=false
        }''')
edit('BrowserController.kt','        val tab=BrowserTab(nextId++,web)','        val tab=BrowserTab(nextId++,web,incognito)')
edit('BrowserController.kt','            safeBrowsingEnabled=true;mediaPlaybackRequiresUserGesture=true','            safeBrowsingEnabled=true;mediaPlaybackRequiresUserGesture=true\n            if(incognito){cacheMode=WebSettings.LOAD_NO_CACHE;databaseEnabled=false;saveFormData=false}')
edit('BrowserController.kt','                tab.url=url;tab.pendingUrl=url;tab.error="";tab.blockedUrl=""','                tab.url=url;tab.pendingUrl=url;tab.error="";tab.blockedUrl="";tab.preview=null;tab.pageIcon=null')
edit('BrowserController.kt','                if(tab.error.isEmpty()) store.visit(url,tab.title)','                if(tab in tabs&&!tab.incognito&&tab.error.isEmpty()){store.visit(url,tab.title);revision++}')
edit('BrowserController.kt','            override fun onReceivedIcon(view:WebView,icon:Bitmap?){if(icon!=null)view.url?.let{icons.remember(it,icon)}}','''            override fun onReceivedIcon(view:WebView,icon:Bitmap?){
                if(tab !in tabs||icon==null)return
                tab.pageIcon=icon
                if(!tab.incognito)view.url?.let{icons.remember(it,icon)}
            }''')
edit('BrowserController.kt','                val child=newTab() ?: return false','                val child=newTab(incognito=tab.incognito) ?: return false')
p=K/'BrowserController.kt';s=p.read_text()
a=s.index('        web.setDownloadListener {');b=s.index('        PageContextMenu(this,tab).install()',a)
s=s[:a]+'''        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if(web.selecting||tab !in tabs)return@setDownloadListener
            runCatching{
                (context as? MainActivity)?.imageDownloads?.download(
                    url,tab.url,userAgent,cookieHeader=cookiesFor(tab,url),
                    disposition=disposition,mimeType=mime,privateMode=tab.incognito,isImage=false
                )
            }.onFailure{notice="無法建立下載，請再試一次"}
        }
'''+s[b:];p.write_text(s)
edit('BrowserController.kt','        tabs.add(tab);activeId=tab.id','        tabs.add(tab);activeId=tab.id;tabCollection=incognito')
edit('BrowserController.kt','        store.recordSearch(input,url);revision++','        if(!privateMode)store.recordSearch(input,url);revision++')
edit('BrowserController.kt','    fun switchTab(id:Int){stopEye();activeId=id;sheet=""}','''    fun switchTab(id:Int){
        val target=tabs.find{it.id==id}?:return
        capturePreview(active);stopEye();activeId=id;tabCollection=target.incognito;sheet=""
    }''')
edit('BrowserController.kt','''        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)
        if(activeId==id)activeId=tabs.lastOrNull()?.id?:0
        if(tabs.isEmpty())newTab()''','''        val privateProfile=if(tab.incognito)privateSession.profileOf(tab.web)else null
        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer)
        tab.refreshContainer.removeAllViews();tab.documentScript?.remove();tab.web.stopLoading();tab.web.destroy()
        tab.preview=null;tab.pageIcon=null;tabs.remove(tab)
        if(tab.incognito&&tabs.none{it.incognito})privateSession.end(privateProfile)
        if(activeId==id)activeId=(tabs.lastOrNull{it.incognito==tab.incognito}?:tabs.lastOrNull())?.id?:0
        if(tabs.isEmpty())newTab(incognito=false)''')
edit('BrowserController.kt','    fun destroy(){exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null);tabs.forEach{(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear()}','''    fun destroy(){
        exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null)
        val privateProfile=tabs.firstOrNull{it.incognito}?.let{privateSession.profileOf(it.web)}
        tabs.forEach{(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.documentScript?.remove();it.web.stopLoading();it.web.destroy();it.preview=null;it.pageIcon=null}
        tabs.clear();mainHandler.removeCallbacksAndMessages(null);privateSession.end(privateProfile)
    }''')
edit('BrowserData.kt','''    fun visit(url: String, title: String) {
        if (!url.startsWith("http")) return
        val items = (listOf(url to title) + history().filterNot { it.first == url }).take(250)
        prefs.edit().putString("history", JSONArray(items.map { JSONObject().put("url", it.first).put("title", it.second) }).toString()).apply()
    }''','''    fun historyEntries():List<HistoryEntry> = runCatching{
        val a=JSONArray(prefs.getString("history","[]"))
        (0 until a.length()).map{val j=a.getJSONObject(it);HistoryEntry(j.getString("url"),j.optString("title"),j.optLong("visited",0))}
    }.getOrDefault(emptyList())
    fun visit(url: String, title: String) {
        if (url.toHttpUrlOrNull()==null) return
        val items=(listOf(HistoryEntry(url,title,System.currentTimeMillis()))+historyEntries().filterNot{it.url==url}).take(250)
        prefs.edit().putString("history",JSONArray(items.map{it.json()}).toString()).apply()
    }
    fun removeHistory(url:String){
        prefs.edit().putString("history",JSONArray(historyEntries().filterNot{it.url==url}.map{it.json()}).toString()).apply()
    }''')
edit('MainActivity.kt','if(browserReady)intent.dataString?.let{if(it.startsWith("https://")||it.startsWith("http://"))controller.newTab(it)}','if(browserReady)intent.dataString?.let{if(it.startsWith("https://")||it.startsWith("http://"))controller.newTab(it,incognito=false)}')
edit('MainActivity.kt','    val suggestionRows=remember(address.text,c.revision,editingAddress){if(editingAddress)AddressHistory.suggestions(address.text,store.searches(),store.history())else emptyList()}','''    val suggestionRows=remember(address.text,c.revision,editingAddress,c.privateMode){if(editingAddress&&!c.privateMode)AddressHistory.suggestions(address.text,store.searches(),store.history())else emptyList()}
    SideEffect{
        if(c.privateMode||(c.sheet=="tabs"&&c.tabCollection))activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }''')
edit('MainActivity.kt','blank=active?.url.isNullOrEmpty(),tabs=c.tabs.size,','blank=active?.url.isNullOrEmpty(),tabs=c.visibleTabCount,')
edit('MainActivity.kt','onTabs={focus.clearFocus();c.sheet="tabs"},','onTabs={focus.clearFocus();c.showTabs()},')
edit('MainActivity.kt','''                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.url.isNullOrEmpty())Home(c,store)''','''                    if(c.privateMode)PrivateModeStrip()
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        if(active?.url.isNullOrEmpty()){if(c.privateMode)PrivateStartPage()else Home(c,store)}''')
edit('MainActivity.kt','''        if(c.sheet in setOf("bookmarks","favorites"))LibraryScreen(c)
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites")){''','''        if(c.sheet in setOf("bookmarks","favorites"))LibraryScreen(c)
        if(c.sheet=="tabs")TabOverview(c)
        if(c.sheet=="downloads")DownloadsScreen(c)
        if(c.sheet=="history")HistoryScreen(c)
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites","tabs","downloads","history")){''')
p=K/'MainActivity.kt';s=p.read_text()
a=s.index('                        "tabs"->{');b=s.index('                        "eye"->',a);s=s[:a]+s[b:]
a=s.index('                        "history"->{');b=s.index('                        "domains"->{',a);s=s[:a]+s[b:];p.write_text(s)
edit('BrowserMenus.kt','DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)','DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,securePolicy=if(c.privateMode||(c.sheet=="tabs"&&c.tabCollection))androidx.compose.ui.window.SecureFlagPolicy.SecureOn else androidx.compose.ui.window.SecureFlagPolicy.Inherit)')
edit('BrowserMenus.kt','"find"->"尋找頁面文字";"blocked"->"攔截紀錄";else->"澄境瀏覽器"','"find"->"尋找頁面文字";"blocked"->"攔截紀錄";"downloads"->"下載";else->"澄境瀏覽器"')
edit('BrowserMenus.kt','"inventory"->"結構清單";"sync"->"Google 同步";"history"->"瀏覽紀錄";"domains"->"網域規則"','"inventory"->"結構清單";"sync"->"Google 同步";"history"->"歷史記錄";"domains"->"網域規則"')
edit('BrowserMenus.kt','''        Shortcut(Icons.Outlined.History,"瀏覽紀錄",Modifier.weight(1f)){c.sheet="history"}
    }
    if(c.domain.isNotEmpty())''','''        Shortcut(Icons.Outlined.Tab,"所有分頁",Modifier.weight(1f)){c.showTabs()}
    }
    MenuGroup("瀏覽工具"){
        MenuRow(Icons.Outlined.Download,"下載","查看澄境瀏覽器的下載項目"){c.sheet="downloads"}
        MenuRow(Icons.Outlined.History,"歷史記錄","搜尋與管理瀏覽足跡"){c.sheet="history"}
        MenuRow(Icons.Outlined.VisibilityOff,"新增無痕分頁","與一般分頁分開的瀏覽空間",navigation=false){c.sheet="";c.newTab(incognito=true)}
    }
    if(c.domain.isNotEmpty())''')
edit('BrowserPageActions.kt','else host.imageDownloads.download(url,page,agent)','else host.imageDownloads.download(url,page,agent,cookieHeader=controller.cookiesFor(tab,url),privateMode=tab.incognito)')
p=ROOT/'app/build.gradle.kts';s=p.read_text();assert 'versionCode = 20' in s and 'versionName = "1.3.1"' in s
p.write_text(s.replace('versionCode = 20','versionCode = 21').replace('versionName = "1.3.1"','versionName = "1.4.0"'))
p=ROOT/'README.md';s=p.read_text().replace('**1.3.1（versionCode 20）**','**1.4.0（versionCode 21）**').replace('[1.3.1 更新說明](docs/releases/1.3.1.md)','[1.4.0 更新說明](docs/releases/1.4.0.md)')
s=s.replace('## 安裝與使用','''## 1.4.0 瀏覽工具

三點選單新增下載清單、可搜尋的歷史記錄與無痕入口。分頁總覽使用一般／無痕兩組卡片，第一層工具列保持 1.3.1 設計。無痕不保存歷史或重開資料，隔離 Cookie 與網站儲存，結束工作階段後清理；手動下載／書籤／收藏仍保留。需要支援 MULTI_PROFILE 與 DELETE_BROWSING_DATA 的 WebView；這不是匿名網路。詳見 [版本範圍與驗證](docs/releases/1.4.0.md)。

## 安裝與使用''');p.write_text(s)
p=ROOT/'app/src/main/assets/privacy.txt';s=p.read_text().replace('更新日期：2026 年 9 月 15 日','更新日期：2026 年 9 月 16 日')
paragraph='''無痕分頁與下載清單
無痕使用與一般分頁隔離的 WebView profile，不新增本機瀏覽及搜尋紀錄，不保存重新開啟清單，也不寫入網站圖示或分頁預覽檔案。一般分頁的預覽也只留在記憶體。關閉全部無痕分頁後清除該工作階段的網站資料；應用意外中止時，下一次啟動會嘗試刪除未完成的暫存 profile，且不恢復舊無痕工作階段。WebView 在使用期間可能把暫存資料寫入 App 私有空間，部分清理由系統非同步完成，這不是磁碟加密或匿名網路。網站、輸入法、網路管理者與網路服務商仍依各自功能及政策處理資料。無痕畫面停用系統截圖及網站表單自動填入；需要支援隔離與完整資料清除的 WebView，無法支援時不以一般模式代替。

你手動建立的書籤、收藏及網站規則仍會保存，依既有同步設定處理。無痕下載前會再次提醒：下載檔案和 Android 系統下載項目會保留到你自行刪除。下載清單只讀取本應用經 Android 系統下載管理員建立的項目，顯示檔名、進度與狀態；不掃描裝置上其他 App 的檔案。開啟下載由你選擇的應用程式處理，並只授予該檔案的讀取權限。

'''
assert s.count('AI 協助（自行選擇）\n')==1
p.write_text(s.replace('AI 協助（自行選擇）\n',paragraph+'AI 協助（自行選擇）\n'))
assert (K/'BrowserControls.kt').read_bytes()==original_controls,'First-level toolbar must remain unchanged'
subprocess.run(['git','diff','--check'],check=True)
print('Exact-match source edits applied; first-level toolbar unchanged. No credentials or signing files were read.')
