from pathlib import Path
import subprocess
R=Path('.')
src=R/'app/src/main/java/tw/techtarian/browser'
for name,expected in {'BrowserController.kt':'d0a576fbeea0d5489ed1d36ae9e27e95d4468caf','BrowserData.kt':'b005a2562e80485fb8d18d1cda60cb8e82da53a3','MainActivity.kt':'44d97f66c708a4215b47fcda7b3a895d356db0e3'}.items():
    assert subprocess.check_output(['git','hash-object',str(src/name)],text=True).strip()==expected

def replace(p,old,new):
    s=p.read_text();assert s.count(old)==1,(p.name,old[:100],s.count(old));p.write_text(s.replace(old,new))
p=src/'BrowserData.kt'
replace(p,'''    fun saveTabs(urls: List<String>,favoriteIds:List<String?> = emptyList()) {
        val links=JSONArray(urls.mapIndexed{i,url->JSONObject().put("url",url).put("id",favoriteIds.getOrNull(i)?:JSONObject.NULL)})
        prefs.edit().putString("tabs",JSONArray(urls).toString()).putString("tab-favorite-links",links.toString()).apply()
    }''','''    fun saveTabs(urls:List<String>,favoriteIds:List<String?> = emptyList(),previewKeys:List<String> = emptyList(),titles:List<String> = emptyList()):Boolean {
        val links=JSONArray(urls.mapIndexed{i,url->JSONObject().put("url",url).put("id",favoriteIds.getOrNull(i)?:JSONObject.NULL)
            .put("previewKey",previewKeys.getOrNull(i)?:TabPreviewStore.newKey()).put("title",titles.getOrNull(i)?:url)})
        // Keep the legacy keys in the same transaction: existing bookmarks and migrations still work.
        return prefs.edit().putString("tabs",JSONArray(urls).toString()).putString("tab-favorite-links",links.toString()).commit()
    }
    internal fun savedTabRecords():List<SavedBrowserTab> {
        val links=runCatching{JSONArray(prefs.getString("tab-favorite-links","[]"))}.getOrDefault(JSONArray())
        val used=mutableSetOf<String>()
        return tabs().mapIndexed{i,url->
            val row=links.optJSONObject(i)?.takeIf{it.optString("url")==url}
            val existing=row?.optString("previewKey")
            val key=existing?.takeIf{TabPreviewStore.validKey(it)&&used.add(it)}?:TabPreviewStore.newKey().also{used.add(it)}
            SavedBrowserTab(key,url,row?.optString("title")?.takeIf{it.isNotBlank()}?.take(180)?:url,
                row?.takeUnless{it.isNull("id")}?.optString("id")?.takeIf{it.isNotBlank()})
        }
    }''')
p=src/'BrowserController.kt'
replace(p,'class BrowserTab(val id: Int, val web: SelectionWebView, val incognito:Boolean=false) {','''class BrowserTab(val id:Int,val web:SelectionWebView,val incognito:Boolean=false,
    val previewKey:String?=if(incognito)null else TabPreviewStore.newKey()) {
    init{require(if(incognito)previewKey==null else TabPreviewStore.validKey(previewKey))}
    internal var previewReady=false
    internal var navigationGeneration=0L
    internal var previewGeneration=0L''')
replace(p,'    val favorites=FavoriteStore(context)','''    val favorites=FavoriteStore(context)
    internal val previews=TabPreviewStore(context)
    private var restoringTabs=false
    private var destroyed=false''')
replace(p,'''        if(tab==null||tab.incognito||tab.url.isEmpty()||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return''','''        if(destroyed||restoringTabs||tab==null||tab !in tabs||tab.incognito||!tab.previewReady||tab.error.isNotEmpty()||
            tab.url.isEmpty()||tab.pendingUrl.isNotEmpty()||tab.id!=activeId||tab.web.selecting||!tab.web.isAttachedToWindow||tab.web.width<=0||tab.web.height<=0)return''')
replace(p,'''            tab.preview=bitmap
        }
    }
    internal fun cookiesFor''','''            tab.previewGeneration++
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
    internal fun cookiesFor''')
replace(p,'''    fun persistTabs() {
        val normal=tabs.filterNot{it.incognito}
        store.saveTabs(normal.map{it.url},normal.map{it.favoriteId})
    }''','''    fun persistTabs():Boolean {
        if(destroyed||restoringTabs)return false
        val normal=tabs.filterNot{it.incognito}
        val saved=store.saveTabs(normal.map{it.url},normal.map{it.favoriteId},normal.map{it.previewKey!!},normal.map{it.title})
        if(!saved)notice="分頁資料未能儲存，請檢查手機儲存空間"
        return saved
    }''')
replace(p,'''    fun restoreTabs() {
        val saved=store.tabs();val links=store.tabFavoriteLinks()
        if(saved.isEmpty())newTab(incognito=false)else saved.forEachIndexed{i,url->
            val link=links.getOrNull(i)?.takeIf{it.first==url}?.second?.let{favorites.get(it)}
            newTab(url,incognito=false)?.favoriteId=link?.takeIf{Domains.scope(it.url)==Domains.scope(url)}?.id
        }
        persistTabs()
    }
    @SuppressLint("SetJavaScriptEnabled")
    fun newTab(url: String = "",incognito:Boolean=active?.incognito==true): BrowserTab? {''','''    fun restoreTabs() {
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
    private fun createTab(url:String,incognito:Boolean,restored:SavedBrowserTab?):BrowserTab? {''')
replace(p,'        val tab=BrowserTab(nextId++,web,incognito)','''        val tab=BrowserTab(nextId++,web,incognito,if(incognito)null else restored?.key?:TabPreviewStore.newKey())
        tab.url=url
        restored?.let{record->
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
        })''')
replace(p,'                tab.preview=null','''                tab.navigationGeneration++
                tab.previewReady=false
                // Keep the last successful thumbnail during reload, offline restore and errors.''')
replace(p,'                if(tab.error.isEmpty()&&!tab.incognito){store.visit(url,tab.title);revision++}','''                if(tab.error.isEmpty()&&!tab.incognito){store.visit(url,tab.title);revision++}
                requestPreviewFrame(tab)''')
replace(p,'            override fun onReceivedTitle(view:WebView,title:String?){tab.title=title?.take(180)?:tab.url}','''            override fun onReceivedTitle(view:WebView,title:String?){tab.title=title?.take(180)?:tab.url;persistTabs()}''')
replace(p,'        tab.preview=null;tab.documentScript?.remove();tab.web.stopLoading()','''        previews.remove(tab.previewKey)
        tab.preview=null;tab.documentScript?.remove();tab.web.stopLoading()''')
replace(p,'    fun destroy(){exitFullscreen();','''    fun destroy(){if(destroyed)return;destroyed=true;exitFullscreen();''')
p=src/'MainActivity.kt'
replace(p,'if(browserReady){android.webkit.CookieManager.getInstance().flush();controller.persistTabs()}','if(browserReady){controller.checkpointTabs();android.webkit.CookieManager.getInstance().flush()}')
replace(p,'''                if(incoming?.startsWith("https://")==true||incoming?.startsWith("http://")==true)controller.newTab(incoming,incognito=false) else controller.restoreTabs()''','''                controller.restoreTabs()
                if(incoming?.startsWith("https://")==true||incoming?.startsWith("http://")==true)controller.newTab(incoming,incognito=false)''')
p=R/'app/build.gradle.kts'
replace(p,'versionCode = 21','versionCode = 22');replace(p,'versionName = "1.4.0"','versionName = "1.4.1"')
p=R/'.github/workflows/android-checks.yml'
replace(p,'tw.techtarian.browser.CollectionsPrivacyTest \\','tw.techtarian.browser.CollectionsPrivacyTest,tw.techtarian.browser.TabPreviewStoreTest,tw.techtarian.browser.TabPreviewPersistenceTest \\')
marker='      - name: Collect source, previews and emulator diagnostics'
replace(p,marker,'''      - name: Verify thumbnails survive actual process restart and close-tab cleanup
        run: |
          adb install -r -t app/build/outputs/apk/debug/app-debug.apk
          adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
          python3 scripts/test-preview-restart.py
'''+marker)
p=R/'README.md'
replace(p,'**1.4.0（versionCode 21）**','**1.4.1（versionCode 22）**')
replace(p,'[1.4.0 更新說明](docs/releases/1.4.0.md)','[1.4.1 更新說明](docs/releases/1.4.1.md)')
p.write_text(p.read_text()+'\n## 1.4.1 分頁快照\n\n一般分頁快照改存 App 內部、排除系統備份，重啟可還原，關閉對應分頁才刪除。無痕仍不儲存快照。舊版未曾保存的圖片需在升級後重新瀏覽頁面才能產生；不改分頁卡片與第一層工具列外觀。\n')
p=R/'app/src/main/assets/privacy.txt'
s=p.read_text().replace('更新日期：2026 年 9 月 15 日','更新日期：2026 年 9 月 17 日');p.write_text(s)
replace(p,'一般分頁的預覽只放在記憶體，不上傳、不寫入磁碟，關閉分頁即捨棄。','一般分頁的預覽快照可能包含目前頁面的可見內容，自 1.4.1 起保存在 App 的內部專用空間，與分頁一起保留，關閉對應分頁時刪除；重開 App 不會刪除。快照不加入 Google 同步、不放入相簿或 Downloads，並排除系統備份。無痕分頁仍不產生或保存快照。清除 App 全部資料或解除安裝會刪除這些本機快照。')
subprocess.run(['git','diff','--check'],check=True)
