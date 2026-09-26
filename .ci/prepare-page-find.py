from pathlib import Path
import subprocess
root=Path('.')
src=root/'app/src/main/java/tw/techtarian/browser'
expected={
 'app/src/main/java/tw/techtarian/browser/BrowserController.kt':'4bef97821e4625edd56148f3e53d4a6cd88560ad',
 'app/src/main/java/tw/techtarian/browser/BrowserMenus.kt':'3c0789d5a6e2dfb59160ab4cb462cb008597a53c',
 'app/src/main/java/tw/techtarian/browser/MainActivity.kt':'264f989b25e53e46d20d5c5e689268ce3605a311',
 'release-smoke/src/androidTest/java/tw/techtarian/browser/smoketests/OptimizedReleaseTest.java':'a3e99c55621883da859feb38a30fbb913c241614',
 'scripts/test-r8-release.py':'805bd1f915e8f053f76d11fc56a71ea25fd8e6e5'}
for name,sha in expected.items():
 assert subprocess.check_output(['git','hash-object',name],text=True).strip()==sha,name

def replace(p,old,new):
 text=p.read_text();assert text.count(old)==1,(str(p),old[:90],text.count(old));p.write_text(text.replace(old,new))
p=src/'BrowserController.kt'
replace(p,'    val favorites=FavoriteStore(context)','    val favorites=FavoriteStore(context)\n    internal val pageFind=PageFindSession()')
replace(p,'        capturePreview(active)\n        stopEye()\n        val web=SelectionWebView(context)','        closeFindInPage()\n        capturePreview(active)\n        stopEye()\n        val web=SelectionWebView(context)')
replace(p,'                prompts.closeFor(tab.id)\n                tab.navigationGeneration++','                prompts.closeFor(tab.id)\n                closeFindInPage(tab.id)\n                tab.navigationGeneration++')
replace(p,'        prompts.closeFor(id)\n        if(id==activeId)stopEye()','        prompts.closeFor(id)\n        closeFindInPage(id)\n        if(id==activeId)stopEye()')
replace(p,'    fun switchTab(id:Int){val tab=tabs.find{it.id==id}?:return;capturePreview(active);','    fun switchTab(id:Int){val tab=tabs.find{it.id==id}?:return;closeFindInPage();capturePreview(active);')
replace(p,'    fun destroy(){if(destroyed)return;destroyed=true;prompts.cancel();','    fun destroy(){if(destroyed)return;destroyed=true;pageFind.close();prompts.cancel();')
p=src/'BrowserMenus.kt'
replace(p,'MenuRow(Icons.Outlined.Search,"尋找頁面文字"){c.sheet="find"}','MenuRow(Icons.Outlined.Search,"尋找頁面文字"){c.openFindInPage()}')
p=src/'MainActivity.kt'
replace(p,'    LaunchedEffect(active?.id){editingAddress=false;focus.clearFocus(force=true);keyboard?.hide()}', '''    LaunchedEffect(active?.id){editingAddress=false;focus.clearFocus(force=true);keyboard?.hide()}
    LaunchedEffect(c.sheet,active?.id,active?.error,active?.imageContent,c.fullScreenView){
        if(c.pageFind.ownerId!=null&&(c.sheet!="find"||c.pageFind.ownerId!=active?.id||
            active?.error?.isNotEmpty()==true||active?.imageContent!=null||c.fullScreenView!=null))c.closeFindInPage()
        if(c.sheet=="find")editingAddress=false
    }''')
replace(p,'            c.sheet.isNotEmpty()->c.sheet=""','            c.sheet=="find"->{focus.clearFocus(force=true);keyboard?.hide();c.closeFindInPage()}\n            c.sheet.isNotEmpty()->c.sheet=""')
replace(p,'                    Box(Modifier.weight(1f).fillMaxWidth()){','''                    if(c.sheet=="find"&&c.pageFind.ownerId==active?.id&&active!=null){
                        key(active.id){FindInPageBar(c.pageFind){c.closeFindInPage()}}
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){''')
replace(p,'"downloads","clear-browsing-data")){','"downloads","clear-browsing-data","find")){')
old='                        "find"->{var text by remember{mutableStateOf("")};SheetTitle("尋找頁面文字");OutlinedTextField(text,{text=it;active?.web?.findAllAsync(it)},label={Text("要找的文字")},modifier=Modifier.fillMaxWidth());Row{TextButton(onClick={active?.web?.findNext(false)}){Text("上一個")};TextButton(onClick={active?.web?.findNext(true)}){Text("下一個")};TextButton(onClick={active?.web?.clearMatches();c.sheet=""}){Text("完成")}}}\n'
replace(p,old,'')
p=root/'scripts/test-r8-release.py'
replace(p,'    "installedReleaseIsActuallyObfuscatedAndNotDebuggable",','    "installedReleaseIsActuallyObfuscatedAndNotDebuggable",\n    "findInPageKeepsTheDocumentVisibleAndCountsMatches",')
p=root/'release-smoke/src/androidTest/java/tw/techtarian/browser/smoketests/OptimizedReleaseTest.java'
replace(p,'    @Test public void installedReleaseIsActuallyObfuscatedAndNotDebuggable() throws Exception {','''    @Test public void findInPageKeepsTheDocumentVisibleAndCountsMatches() throws Exception {
        menu();click("尋找頁面文字");
        assertFalse(device.hasObject(By.desc("關閉選單")));
        require(By.text("R8 測試元件")); // Same live document still visible, not a full-screen sheet.
        UiObject2 input=require(By.clazz("android.widget.EditText"));
        input.setText("R8");
        require(By.desc("第 1 筆，共 2 筆"));
        require(By.desc("下一筆符合文字")).click();
        require(By.desc("第 2 筆，共 2 筆"));
        require(By.desc("上一筆符合文字")).click();
        require(By.desc("第 1 筆，共 2 筆"));
        require(By.clazz("android.widget.EditText")).setText("no-such-search-marker");
        require(By.desc("找不到符合文字"));
        require(By.desc("關閉頁面搜尋")).click();
        assertTrue(device.wait(Until.gone(By.desc("關閉頁面搜尋")),5000));
        require(By.text("R8 測試元件"));
        require(By.desc("瀏覽器選單"));
    }

    @Test public void installedReleaseIsActuallyObfuscatedAndNotDebuggable() throws Exception {''')
subprocess.run(['git','diff','--check'],check=True)
