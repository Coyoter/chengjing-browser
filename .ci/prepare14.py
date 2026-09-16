from pathlib import Path
import subprocess

root=Path.cwd()
def edit(name,pairs):
    path=root/name
    s=path.read_text()
    for old,new in pairs:
        assert old in s,(name,old[:100])
        s=s.replace(old,new,1)
    path.write_text(s)
base='app/src/main/java/tw/techtarian/browser/'
edit(base+'BrowserData.kt',[
('    fun clearHistory() {','''    fun removeHistory(url:String) {
        val rows=history().filterNot{it.first==url}
        prefs.edit().putString("history",JSONArray(rows.map{JSONObject().put("url",it.first).put("title",it.second)}).toString()).apply()
    }
    fun clearHistory() {''')])
edit(base+'BrowserController.kt',[
('class BrowserTab(val id: Int, val web: SelectionWebView) {','''class BrowserTab(val id: Int, val web: SelectionWebView, val incognito:Boolean=false) {
    var preview by mutableStateOf<Bitmap?>(null)
    val warnings=if(incognito)CertificateWarnings()else CertificateWarnings.session'''),
('    var activeId by mutableIntStateOf(0)','''    var activeId by mutableIntStateOf(0)
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
        if(tab==null||tab.incognito||tab.url.isEmpty()||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return
        runCatching{
            val width=300
            val height=(tab.web.height.toFloat()*width/tab.web.width).toInt().coerceIn(1,400)
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            canvas.scale(width.toFloat()/tab.web.width,width.toFloat()/tab.web.width)
            tab.web.draw(canvas)
            tab.preview=bitmap
        }
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
    }'''),
('    fun persistTabs() = store.saveTabs(tabs.map { it.url },tabs.map{it.favoriteId})','''    fun persistTabs() {
        val normal=tabs.filterNot{it.incognito}
        store.saveTabs(normal.map{it.url},normal.map{it.favoriteId})
    }'''),
('if(saved.isEmpty())newTab()else saved.forEachIndexed','if(saved.isEmpty())newTab(incognito=false)else saved.forEachIndexed'),
('newTab(url)?.favoriteId=','newTab(url,incognito=false)?.favoriteId='),
('    fun newTab(url: String = ""): BrowserTab? {','''    fun newTab(url: String = "",incognito:Boolean=active?.incognito==true): BrowserTab? {
        if(incognito&&!privateSession.supported){sheet="";notice="請更新 Android System WebView，才能使用資料隔離的無痕分頁";return null}'''),
('        stopEye()\n        val web=SelectionWebView(context)','''        capturePreview(active)
        stopEye()
        val web=SelectionWebView(context)
        if(incognito)try{privateSession.attach(web)}catch(_:Exception){web.destroy();notice="無法建立隔離的無痕工作階段，未開啟網頁";return null}'''),
('val tab=BrowserTab(nextId++,web)','val tab=BrowserTab(nextId++,web,incognito)'),
('        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)','''        if(incognito){
            web.settings.cacheMode=WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION")
            web.settings.saveFormData=false
            web.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            if(android.os.Build.VERSION.SDK_INT>=29)web.importantForContentCapture=android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        cookiesFor(tab).setAcceptThirdPartyCookies(web,false)'''),
('                tab.url=url;tab.pendingUrl=url;tab.error="";tab.blockedUrl=""','                tab.preview=null\n                tab.url=url;tab.pendingUrl=url;tab.error="";tab.blockedUrl=""'),
('if(tab.error.isEmpty()) store.visit(url,tab.title)','if(tab.error.isEmpty()&&!tab.incognito){store.visit(url,tab.title);revision++}'),
('override fun onReceivedIcon(view:WebView,icon:Bitmap?){if(icon!=null)','override fun onReceivedIcon(view:WebView,icon:Bitmap?){if(icon!=null&&!tab.incognito)'),
('val child=newTab() ?: return false','val child=newTab(incognito=tab.incognito) ?: return false'),
('        tabs.add(tab);activeId=tab.id','        tabs.add(tab);activeId=tab.id;updatePrivacyWindow()'),
('store.recordSearch(input,url);revision++','recordSearchFor(active,input,url);revision++'),
('    fun openFavorite(favorite:Favorite)','    internal fun recordSearchFor(tab:BrowserTab?,input:String,url:String){if(tab?.incognito!=true)store.recordSearch(input,url)}\n    fun openFavorite(favorite:Favorite)'),
('    fun switchTab(id:Int){stopEye();activeId=id;sheet=""}','    fun switchTab(id:Int){if(tabs.none{it.id==id})return;capturePreview(active);stopEye();activeId=id;sheet="";updatePrivacyWindow()}'),
('        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)\n        if(activeId==id)activeId=tabs.lastOrNull()?.id?:0\n        if(tabs.isEmpty())newTab()\n        persistTabs()','''        tab.preview=null;tab.documentScript?.remove();tab.web.stopLoading()
        (tab.refreshContainer.parent as? ViewGroup)?.removeView(tab.refreshContainer);tab.refreshContainer.removeAllViews();tab.web.destroy();tabs.remove(tab)
        if(tab.incognito&&tabs.none{it.incognito})privateSession.clear()
        if(activeId==id)activeId=(tabs.lastOrNull{it.incognito==tab.incognito}?:tabs.lastOrNull())?.id?:0
        if(tabs.isEmpty())newTab(incognito=false)
        persistTabs();updatePrivacyWindow()'''),
('    fun beginEye(){','    fun closePrivateTabs(){tabs.filter{it.incognito}.map{it.id}.forEach{closeTab(it)}}\n    fun beginEye(){'),
('    fun destroy(){exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null);tabs.forEach{(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear()}','    fun destroy(){exitFullscreen();gemma.close();icons.close();fileCallback?.onReceiveValue(null);mainHandler.removeCallbacksAndMessages(null);tabs.forEach{it.preview=null;it.documentScript?.remove();it.web.stopLoading();(it.refreshContainer.parent as? ViewGroup)?.removeView(it.refreshContainer);it.refreshContainer.removeAllViews();it.web.destroy()};tabs.clear();privateSession.clear()}')])
p=root/(base+'BrowserController.kt');s=p.read_text()
a=s.index('        web.webViewClient=');b=s.index('        web.webChromeClient=')
part=s[a:b].replace('CertificateWarnings.session','tab.warnings')
part=part.replace('tabs.forEach{it.certificateWarning=tab.warnings.messageFor(it.url.ifBlank{it.pendingUrl})}','tabs.filter{it.incognito==tab.incognito}.forEach{it.certificateWarning=it.warnings.messageFor(it.url.ifBlank{it.pendingUrl})}')
s=s[:a]+part+s[b:]
a=s.index('                runCatching {\n                    val req=DownloadManager.Request');b=s.index('\n            }.show()',a)
s=s[:a]+'                downloadFor(tab,url,tab.url,userAgent,mime,disposition,imageOnly=false)'+s[b:]
p.write_text(s)
edit(base+'BrowserPageActions.kt',[('else host.imageDownloads.download(url,page,agent)','else controller.downloadFor(tab,url,page,agent)')])
edit(base+'BrowserImageDownloads.kt',[
('private data class Pending(val url:String,val page:String,val userAgent:String)','private data class Pending(val url:String,val page:String,val userAgent:String,val cookie:String?,val mime:String?,val disposition:String?,val imageOnly:Boolean)'),
('fun download(rawUrl:String,page:String,userAgent:String):Long?','fun download(rawUrl:String,page:String,userAgent:String,cookieHeader:String?=CookieManager.getInstance().getCookie(rawUrl),mimeHint:String?=null,disposition:String?=null,imageOnly:Boolean=true):Long?'),
('val request=Pending(url,page,userAgent)','val request=Pending(url,page,userAgent,cookieHeader,mimeHint,disposition,imageOnly)'),
('val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf{it.startsWith("image/")}','val mime=task.mime?.takeIf{it.isNotBlank()}?:MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf{!task.imageOnly||it.startsWith("image/")}'),
('URLUtil.guessFileName(task.url,null,mime)','URLUtil.guessFileName(task.url,task.disposition,mime)'),
('.setDescription("澄境瀏覽器 · 圖片下載")','.setDescription(if(task.imageOnly)"澄境瀏覽器 · 圖片下載"else"澄境瀏覽器 · 檔案下載")'),
('CookieManager.getInstance().getCookie(task.url)?.takeIf','task.cookie?.takeIf'),
('notice("圖片已加入系統下載佇列，可在通知或「下載」查看結果")','notice("已加入系統下載佇列，可在三點選單的「下載」查看結果")')])
p=root/(base+'MainActivity.kt');s=p.read_text()
s=s.replace('val suggestionRows=remember(address.text,c.revision,editingAddress){if(editingAddress)','val suggestionRows=remember(address.text,c.revision,editingAddress,active?.incognito){if(editingAddress&&active?.incognito!=true)')
s=s.replace('    val cs=if(dark)Dark else Light\n','    val cs=if(dark)Dark else Light\n    SideEffect{c.updatePrivacyWindow()}\n')
s=s.replace('tabs=c.tabs.size,','tabs=c.activeTabCount,').replace('onTabs={focus.clearFocus();c.sheet="tabs"}','onTabs={focus.clearFocus();c.openTabOverview()}')
s=s.replace('                        if(active?.url.isNullOrEmpty())Home(c,store)','                        if(active?.url.isNullOrEmpty()){if(active?.incognito==true)IncognitoHome()else Home(c,store)}')
s=s.replace('                    if(c.eye) {','''                    if(active?.incognito==true)Text("無痕瀏覽",Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(horizontal=18.dp,vertical=5.dp).testTag("incognito-indicator"),fontSize=11.sp,color=cs.onSurfaceVariant)
                    if(c.eye) {''',1)
s=s.replace('        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites")){','''        if(c.sheet in setOf("tabs","history","downloads"))BrowserPanel(c){
            PanelHeader(c){c.sheet="menu"}
            when(c.sheet){"tabs"->TabOverview(c);"history"->HistoryScreen(c);"downloads"->DownloadsScreen(c)}
        }
        if(c.sheet.isNotEmpty()&&c.sheet !in setOf("bookmarks","favorites","tabs","history","downloads")){''')
a=s.index('                        "tabs"->{');b=s.index('                        "eye"->',a);s=s[:a]+s[b:]
a=s.index('                        "history"->{');b=s.index('                        "domains"->',a);s=s[:a]+s[b:]
s=s.replace('controller.newTab(incoming) else controller.restoreTabs()','controller.newTab(incoming,incognito=false) else controller.restoreTabs()')
s=s.replace('controller.newTab(it)}','controller.newTab(it,incognito=false)}')
p.write_text(s)
edit(base+'BrowserMenus.kt',[
('SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->','''SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->
            if(c.privateScreen)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)'''),
('"history"->"瀏覽紀錄";','"history"->"歷史記錄";"downloads"->"下載";'),
('{c.newTab();c.sheet=""}','{c.newTab(incognito=false);c.sheet=""}'),
('    if(c.domain.isNotEmpty())MenuGroup("目前頁面"){','''    MenuGroup("瀏覽工具"){
        MenuRow(Icons.Outlined.PrivacyTip,"新增無痕分頁","與一般分頁分開的瀏覽工作階段",navigation=false){if(c.newTab(incognito=true)!=null)c.sheet=""}
        MenuRow(Icons.Outlined.Download,"下載","檢視澄境瀏覽器下載的項目"){c.sheet="downloads"}
        MenuRow(Icons.Outlined.History,"歷史記錄","搜尋與管理瀏覽過的頁面"){c.sheet="history"}
    }
    if(c.domain.isNotEmpty())MenuGroup("目前頁面"){''')])
p=root/(base+'ConnectionPanel.kt');s=p.read_text().replace('CertificateWarnings.session.originsFor(','(c.active?.warnings?:CertificateWarnings.session).originsFor(');p.write_text(s)
edit('app/build.gradle.kts',[('versionCode = 20','versionCode = 21'),('versionName = "1.3.1"','versionName = "1.4.0"')])
p=root/'README.md';s=p.read_text().replace('**1.3.1（versionCode 20）**','**1.4.0（versionCode 21）**').replace('[1.3.1 更新說明](docs/releases/1.3.1.md)','[1.4.0 更新說明](docs/releases/1.4.0.md)');s+='\n## 1.4.0 瀏覽工具\n\n三點選單加入下載、歷史記錄與無痕分頁；一般／無痕双分頁集與卡片預覽保持第一層工具列不變。無痕需支援 WebView 資料隔離與清理；主動保存的檔案、書籤及收藏仍保留，詳見更新說明。\n';p.write_text(s)
p=root/'app/src/main/assets/privacy.txt';s=p.read_text().replace('更新日期：2026 年 9 月 15 日','更新日期：2026 年 9 月 16 日');s+='''

無痕分頁與下載清單（1.4.0）
無痕分頁使用與一般分頁不同的 WebView Profile，不共用 Cookie、快取與網站儲存。不加入本機瀏覽或搜尋記錄，不保存分頁網址供重開復原，也不產生分頁預覽縮圖。關閉全部無痕分頁時會請求清理此 Profile 的 Cookie、網路快取及網站儲存；清理是非同步工作。若程序異常終止，下一次啟動會清理舊的無痕 Profile，且不再使用該工作階段。這不保證對作業系統或實體鑑識完全無痕，亦不隱藏你對網站、雇主或網路供應商的活動。
你主動建立的書籤、收藏、網站規則及系統下載會保留。無痕下載前會再次告知檔案及系統下載記錄的保存；系統分享、AI 分析等主動外送行為仍依原本的確認與服務政策處理。需要支援資料隔離及清理的 Android System WebView，不支援時會提示更新，不以一般分頁偽裝成無痕。
下載清單只查詢此 App 建立的系統下載記錄，不掃描手機其他 App 的檔案；已被系統移除的記錄不保證可復原。一般分頁的預覽只放在記憶體，不上傳、不寫入磁碟，關閉分頁即捨棄。
''';p.write_text(s)
subprocess.run(['git','diff','--exit-code','553dd88faeafc6c635b4cc00891e0daa0bb5d0c0','--',base+'BrowserControls.kt'],check=True)
subprocess.run(['git','diff','--check'],check=True)
