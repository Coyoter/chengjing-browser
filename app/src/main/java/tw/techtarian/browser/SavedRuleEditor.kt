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
        check(c.activeId==tabId&&c.active?.url==url){bt(R.string.msg_9567c32b9a28)}
        check((c.draft?:c.site)==original){bt(R.string.msg_95bc4a80d511)}
        check(c.store.get(original.domain)==savedAtOpen){bt(R.string.msg_9ba6acba3917)}
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
    Text(if(session.edit==null)bt(R.string.msg_b654f1e4475a)else session.edit.selector,fontSize=16.sp)
    MenuGroup(bt(R.string.msg_30b5133da158)){
        MenuRow(Icons.Outlined.AutoAwesome,bt(R.string.msg_dfb02399c259),bt(R.string.msg_a73d81627121)){
            runCatching{session.checkCurrent(c);session.value();c.sheet="rule-ai"}.onFailure{error=it.localizedMessage.orEmpty()}
        }
    }
    Text(bt(R.string.msg_6882b571d47c),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    CodeInput(if(session.edit==null)bt(R.string.msg_9e1d4222ab1b)else bt(R.string.msg_16a3beab3f94),session.css,{session.css=it;error=""},"saved-rule-css")
    CodeInput("JavaScript",session.js,{session.js=it;error=""},"saved-rule-js")
    CodeInput("HTML",session.html,{session.html=it;error=""},"saved-rule-html")
    if(session.edit!=null)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected=session.mode=="append",onClick={session.mode="append"},label={Text(bt(R.string.msg_f3a2be83fce5))})
        FilterChip(selected=session.mode=="replace",onClick={session.mode="replace"},label={Text(bt(R.string.msg_c7518df827c2))})
    }
    Text(bt(R.string.msg_d727b0d7f49a),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("saved-rule-error"))
    Button(enabled=!busy,onClick={busy=true;scope.launch{
        runCatching{session.checkCurrent(c);val value=session.value();c.validateCode(value);session.checkCurrent(c);check(session.value()==value){bt(R.string.msg_5ef814324284)};c.saveSite(value,reload=true);c.stopEye();c.sheet="";c.notice=bt(R.string.msg_2a54a31cbd76)}
            .onFailure{error=it.localizedMessage.orEmpty()};busy=false
    }},modifier=Modifier.fillMaxWidth().testTag("save-rule")){Text(bt(R.string.msg_d5ae259c7a80))}
    TextButton(onClick={deleting=true},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){
        Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(bt(R.string.msg_f5272b216037))
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text(bt(R.string.msg_c77c63dd2049))},text={Text(bt(R.string.msg_0f0cc78443f7 ,if(session.edit==null)bt(R.string.msg_67867e6d6cc2)else bt(R.string.msg_60f997e432a2)))},dismissButton={TextButton(onClick={deleting=false}){Text(bt(R.string.msg_2cd0f3be8738))}},confirmButton={TextButton(onClick={
        runCatching{session.checkCurrent(c);val value=if(session.edit==null)session.original.copy(css="",js="",html="")else session.original.copy(edits=session.original.edits.filterNot{it.id==session.editId});c.saveSite(value,reload=true);c.stopEye();c.sheet="rules";c.notice=bt(R.string.msg_eb52c71529f3)}.onFailure{error=it.localizedMessage.orEmpty()};deleting=false
    }){Text(bt(R.string.msg_3c8f5b363ab3),color=MaterialTheme.colorScheme.error)}})
}
