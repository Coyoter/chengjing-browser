package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal class RuleEditSession(val original:SiteRules,val savedAtOpen:SiteRules,val editId:String?,val tabId:Int,val url:String) {
    val edit=editId?.let{id->original.edits.single{it.id==id}}
    var css by mutableStateOf(edit?.css?:original.css)
    var js by mutableStateOf(edit?.js?:original.js)
    var html by mutableStateOf(edit?.html?:original.html)
    var mode by mutableStateOf(edit?.mode?:"append")
    fun value():SiteRules = edit?.let{old->
        val revised=old.copy(css=css,js=js,html=html,mode=mode).also{it.validate()}
        original.copy(edits=original.edits.map{if(it.id==old.id)revised else it})
    }?:original.copy(css=css,js=js,html=html)
    fun checkCurrent(c:BrowserController){
        check(c.activeId==tabId&&c.active?.url==url){"網頁已變更，請重新開啟這項規則"}
        check((c.draft?:c.site)==original){"規則已更新，請重新開啟，避免覆蓋其他修改"}
        check(c.store.get(original.domain)==savedAtOpen){"規則已由其他操作更新，請重新開啟"}
    }
}

internal fun BrowserController.editSavedRule(editId:String?=null){
    ruleEdit=RuleEditSession(draft?:site,site,editId,activeId,active?.url.orEmpty())
    sheet="rule-editor"
}

@Composable internal fun SavedRuleEditor(c:BrowserController){
    val session=c.ruleEdit?:return
    val scope=rememberCoroutineScope()
    var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf(false)}
    Text(session.original.domain,fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
    Text(if(session.edit==null)"網站 CSS / JS / HTML"else session.edit.selector,fontSize=16.sp)
    MenuGroup("讓修改更進一步"){
        MenuRow(Icons.Outlined.AutoAwesome,"請 AI 修改這項規則","結合原始 HTML、目前程式碼與你的新想法"){
            runCatching{session.checkCurrent(c);session.value();c.sheet="rule-ai"}.onFailure{error=it.localizedMessage.orEmpty()}
        }
    }
    Text("可直接編輯下方內容。儲存會更新原規則；其他網站修改會保留。",fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    CodeInput(if(session.edit==null)"CSS 樣式"else"元件 CSS 屬性",session.css,{session.css=it;error=""},"saved-rule-css")
    CodeInput("JavaScript",session.js,{session.js=it;error=""},"saved-rule-js")
    CodeInput("HTML",session.html,{session.html=it;error=""},"saved-rule-html")
    if(session.edit!=null)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected=session.mode=="append",onClick={session.mode="append"},label={Text("加在元件末尾")})
        FilterChip(selected=session.mode=="replace",onClick={session.mode="replace"},label={Text("取代元件內容")})
    }
    Text("適用此網域及子網域。JavaScript 可存取頁面或發出請求，請確認內容後再儲存。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("saved-rule-error"))
    Button(enabled=!busy,onClick={busy=true;scope.launch{
        runCatching{session.checkCurrent(c);val value=session.value();c.validateCode(value);session.checkCurrent(c);check(session.value()==value){"編輯內容已變更，請再次儲存"};c.saveSite(value,reload=true);c.stopEye();c.sheet="";c.notice="已更新原規則並套用"}
            .onFailure{error=it.localizedMessage.orEmpty()};busy=false
    }},modifier=Modifier.fillMaxWidth().testTag("save-rule")){Text("儲存並套用")}
    TextButton(onClick={deleting=true},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){
        Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("刪除這項規則")
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("刪除這項規則？")},text={Text("${if(session.edit==null)"此網站的自訂 CSS、JavaScript 與 HTML"else"這項元件修改"}將移除，其他設定會保留。")},dismissButton={TextButton(onClick={deleting=false}){Text("取消")}},confirmButton={TextButton(onClick={
        runCatching{session.checkCurrent(c);val value=if(session.edit==null)session.original.copy(css="",js="",html="")else session.original.copy(edits=session.original.edits.filterNot{it.id==session.editId});c.saveSite(value,reload=true);c.stopEye();c.sheet="rules";c.notice="已刪除這項規則"}.onFailure{error=it.localizedMessage.orEmpty()};deleting=false
    }){Text("刪除",color=MaterialTheme.colorScheme.error)}})
}
