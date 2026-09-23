package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun WebsiteAiEntry(c:BrowserController){
    MenuGroup("目前網站 · ${c.domain}"){
        MenuRow(androidx.compose.material.icons.Icons.Outlined.AutoAwesome,"想做什麼嘗試？直接問 AI","描述整體想法，不限於所選元件"){c.aiElement=null;c.sheet="develop-ai"}
    }
}

@Composable internal fun ElementEditor(c:BrowserController){
    val selected=remember{c.selection}?:return
    val original=remember{c.draft?:c.site};val tab=remember{c.activeId};val url=remember{c.active?.url};val replace=remember{c.editingHtml}
    var css by remember{mutableStateOf("")};var js by remember{mutableStateOf("")};var html by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(replace)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){if(replace){runCatching{val raw=c.js("window.__chengjingEye?.innerHTML(${JSONObject.quote(selected.selector)})");check(raw!="null"){"元件無法讀取，請重新選取"};JSONArray("[$raw]").getString(0)}.onSuccess{html=it}.onFailure{error=it.localizedMessage.orEmpty()};loading=false}}
    Text(selected.label,fontSize=16.sp)
    Text("${selected.selector}\n目前符合 ${selected.count} 個元件；儲存後套用至 ${original.domain} 與子網域中符合此結構的元件。",fontSize=12.sp,lineHeight=19.sp)
    if(!replace){
        CodeInput("元件 CSS 屬性",css,{css=it},"element-css","color: #147a64; line-height: 1.8;")
        CodeInput("元件 JavaScript",js,{js=it},"element-js","// element 代表目前符合規則的元件\nelement.setAttribute('title', '我的閱讀提示');")
    }
    CodeInput(if(replace)"元件內部 HTML"else"新增 HTML（放在元件內最後方）",html,{html=it},"element-html","<p>我的閱讀提醒</p>")
    Text("${if(replace)"這會替換符合元件的內部內容，可能影響原本互動。"else"CSS／JS／HTML 可任選填寫。"}HTML 中的 script 不會執行；需要互動時請使用 JavaScript。JS 可讀取網頁或發出請求，請只套用你了解的內容。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,modifier=Modifier.testTag("developer-error"),color=MaterialTheme.colorScheme.error)
    Button(enabled=!loading,onClick={scope.launch{
        runCatching{
            check(c.activeId==tab&&c.active?.url==url){"網頁已變更，請重新選取"}
            check(replace||css.isNotBlank()||js.isNotBlank()||html.isNotBlank()){"請先填寫要新增的內容"}
            val edit=PageEdit(selector=selected.selector,css=css,js=js,html=html,mode=if(replace)"replace"else"append");edit.validate()
            c.validateSelectors(listOf(edit.selector))?.let{error(it)}
            check(original.edits.size<100){"網站修改已達上限，請先整理已儲存的修改"}
            val value=original.copy(edits=original.edits+edit);c.validateCode(value);c.saveSite(value,reload=true);c.stopEye();c.sheet="";c.notice="已儲存並套用；可在天眼設定復原上一次儲存"
        }.onFailure{error=it.localizedMessage.orEmpty()}
    }},modifier=Modifier.fillMaxWidth()){Text(if(loading)"正在讀取元件…"else"儲存並套用")}
}

@Composable internal fun CodeInput(label:String,value:String,onChange:(String)->Unit,tag:String,placeholder:String=""){
    OutlinedTextField(value,onChange,label={Text(label)},placeholder={Text(placeholder,fontSize=12.sp)},textStyle=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=18.sp),modifier=Modifier.fillMaxWidth().testTag(tag),minLines=3,maxLines=7)
}

@Composable internal fun DeveloperAiPanel(c:BrowserController,revision:RuleEditSession?=null,onSettings:()->Unit){
    c.revision
    val local=c.store.aiProvider=="gemma"
    var analysisJob by remember{mutableStateOf<Job?>(null)}
    val selected=remember{if(revision==null)c.aiElement else null};val original=remember{revision?.value()?:c.draft?:c.site};val tab=remember{c.activeId};val url=remember{c.active?.url}
    val scope=rememberCoroutineScope();var problem by remember{mutableStateOf("")};var consent by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var proposal by remember{mutableStateOf<DeveloperProposal?>(null)};var analyzedDocument by remember{mutableStateOf("")}
    Text(if(revision!=null)"讓原本的修改更合心意"else if(selected==null)"想做什麼嘗試？直接問 AI"else"請 AI 檢查這項修改",fontSize=20.sp)
    Text(if(revision!=null)"${original.domain}\n${revision.edit?.selector?:"網站 CSS / JS / HTML"}\nAI 會讀取本次頁面載入、套用天眼前的 HTML 與目前編輯內容，再依你的新要求修訂這項規則。"else if(selected==null)"目前網站：${original.domain}\n可對整體版面提出建議。分析依據是目前開啟頁面的有限結構，不代表已檢查網站所有頁面。"else"所選元件：${selected.label}\n元件修改以這個選擇器為目標：${selected.selector}",fontSize=13.sp,lineHeight=21.sp)
    OutlinedTextField(problem,{problem=it;proposal=null},enabled=!busy,label={Text(if(revision!=null)"這次想如何調整？"else if(selected==null)"你想讓這個網站有什麼變化？"else"這個元件想怎麼改？哪裡需要檢查？")},modifier=Modifier.fillMaxWidth().testTag("developer-ai-problem"),minLines=3,maxLines=5)
    if(revision!=null)Text("HTML 會移除表單內容、script 與事件屬性，最多提供 ${if(local)"6,000"else"24,000"} 個字元；可能包含頁面文字，不涵蓋其他頁面或後續動態內容。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(selected!=null)Text("CSS／HTML 會套用到所選元件；JavaScript 仍具有頁面程式碼的權限，請檢查實際影響範圍。",fontSize=12.sp,lineHeight=19.sp)
    if(local){
        Text("使用 Gemma 4 本機分析，不需要 API Key，這次分析的網頁結構、程式碼及需求不會傳送到雲端。",fontSize=12.sp,lineHeight=19.sp)
        if(!c.gemma.ready)GemmaSetupPanel(c)
    }else{
        Text(if(revision!=null)"本次會將網域、清理後的原始 HTML（可能包含私人頁面文字）、這項自訂規則與新要求，傳給 OpenRouter 及所選模型供應商。不讀取 Cookie 或表單值。"else"本次分析會將網域、有限元件結構、已儲存的自訂程式碼及你輸入的需求，傳送給 OpenRouter 與所選模型供應商。程式碼或問題可能包含私人內容，請先確認；不主動讀取 Cookie、表單值或文章內文。",fontSize=12.sp,lineHeight=19.sp)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it},enabled=!busy);Text("同意傳送上述資料供本次分析",fontSize=13.sp)}
    }
    TextButton(onClick=onSettings){Text("選擇 AI 執行方式")}
    if(!local&&!c.store.hasKey())Button(onClick=onSettings){Text("設定 OpenRouter API Key")}
    else Button(enabled=!busy&&(local||consent)&&problem.isNotBlank()&&(!local||c.gemma.ready),onClick={busy=true;error="";proposal=null;analysisJob=scope.launch{
        runCatching{
            check(c.activeId==tab&&c.active?.url==url){"網頁已變更，請重新開啟 AI"}
            revision?.checkCurrent(c)
            analyzedDocument=c.js("performance.timeOrigin")
            val raw=if(revision!=null)c.js("window.__chengjingEye?.originalSource(${revision.edit?.selector?.let{JSONObject.quote(it)}?:"null"})")else c.js("window.__chengjingEye?.snapshot(${selected?.selector?.let{JSONObject.quote(it)}?:"null"})")
            check(raw!="null"){if(revision?.edit!=null)"無法讀取這項規則的原始 HTML。請先開啟包含該元件的網頁，重新載入後再試。"else"頁面尚未準備好，請重新載入後再試"}
            val data=JSONArray("[$raw]").getString(0)
            check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){"網頁已變更，請重新分析"}
            if(revision!=null)c.developerRevision(problem,data,original,revision.editId)else c.developerSuggestion(problem,data,original,selected?.selector)
        }.onSuccess{proposal=it}.onFailure{error=if(it is CancellationException)"已取消分析"else it.localizedMessage.orEmpty()};busy=false
    }},modifier=Modifier.fillMaxWidth()){Text(if(busy)"正在分析…"else if(local)"使用 Gemma 4 分析"else"傳送並取得建議")}
    if(analysisJob?.isActive==true)TextButton(onClick={if(local)c.gemma.cancelInference();analysisJob?.cancel()}){Text("取消分析")}
    proposal?.let{p->
        SettingsGroup("AI 建議"){
            Text(p.explanation,fontSize=14.sp,lineHeight=22.sp)
            if(p.changes.isEmpty())Text("這次沒有產生可套用的修改。")else p.changes.forEach{Text(it,fontSize=13.sp)}
            Text("儲存範圍：${original.domain} 及其子網域。AI 建議尚未在你的網站實際驗證；請查看程式碼與變更後再套用。",fontSize=12.sp,lineHeight=19.sp)
            var code by remember{mutableStateOf(false)}
            TextButton(onClick={code=!code}){Text(if(code)"收合修改內容"else"查看完整修改內容")}
            if(code)Text(p.result.json().toString(2),fontSize=11.sp,fontFamily=FontFamily.Monospace)
            Button(enabled=!busy&&p.changes.isNotEmpty(),onClick={busy=true;scope.launch{
                runCatching{
                    check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){"網頁已變更，請重新分析"}
                    check((c.draft?:c.site)==(revision?.original?:original)){"設定已變更，請重新分析"}
                    revision?.checkCurrent(c)
                    c.validateSelectors(p.selectors)?.let{error(it)}
                    c.validateCode(p.result)
                    check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){"網頁已變更，請重新分析"}
                    check((c.draft?:c.site)==(revision?.original?:original)){"設定已變更，請重新分析"}
                    revision?.checkCurrent(c)
                    c.saveSite(p.result,reload=true);c.stopEye();c.sheet="";c.notice="已套用 AI 建議；可在天眼設定復原上一次儲存"
                }.onFailure{error=it.localizedMessage.orEmpty()};busy=false
            }},modifier=Modifier.fillMaxWidth()){Text(if(revision!=null)"更新原規則並套用"else"一鍵套用建議")}
            Text("可復原上一份設定；程式已發出的請求或網站操作無法一併撤銷。",fontSize=12.sp)
        }
    }
    if(error.isNotEmpty())Text(error,modifier=Modifier.testTag("developer-error"),color=MaterialTheme.colorScheme.error)
}
