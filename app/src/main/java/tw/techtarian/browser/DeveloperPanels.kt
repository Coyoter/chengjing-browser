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
    MenuGroup(bt(R.string.msg_ad1459efecaa ,c.domain)){
        MenuRow(androidx.compose.material.icons.Icons.Outlined.AutoAwesome,bt(R.string.msg_9cde4de25fb1),bt(R.string.msg_7a3bfa84b015)){c.aiElement=null;c.sheet="develop-ai"}
    }
}

@Composable internal fun ElementEditor(c:BrowserController){
    val selected=remember{c.selection}?:return
    val original=remember{c.draft?:c.site};val tab=remember{c.activeId};val url=remember{c.active?.url};val replace=remember{c.editingHtml}
    var css by remember{mutableStateOf("")};var js by remember{mutableStateOf("")};var html by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var loading by remember{mutableStateOf(replace)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){if(replace){runCatching{val raw=c.js("window.__chengjingEye?.innerHTML(${JSONObject.quote(selected.selector)})");check(raw!="null"){bt(R.string.msg_15dd14db5d9f)};JSONArray("[$raw]").getString(0)}.onSuccess{html=it}.onFailure{error=it.localizedMessage.orEmpty()};loading=false}}
    Text(selected.label,fontSize=16.sp)
    Text(bt(R.string.msg_8ddd4dc4aaf0 ,selected.selector,selected.count,original.domain),fontSize=12.sp,lineHeight=19.sp)
    if(!replace){
        CodeInput(bt(R.string.msg_16a3beab3f94),css,{css=it},"element-css","color: #147a64; line-height: 1.8;")
        CodeInput(bt(R.string.msg_d69d4cc47d52),js,{js=it},"element-js",bt(R.string.msg_1a751a7d6071))
    }
    CodeInput(if(replace)bt(R.string.msg_1bb0c8626773)else bt(R.string.msg_f8ff75626431),html,{html=it},"element-html",bt(R.string.msg_b3ef3187ef3f))
    Text(bt(R.string.msg_b8412099e607 ,if(replace)bt(R.string.msg_f5b7ee522a1d)else bt(R.string.msg_39afab2bf5bd)),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(error.isNotEmpty())Text(error,modifier=Modifier.testTag("developer-error"),color=MaterialTheme.colorScheme.error)
    Button(enabled=!loading,onClick={scope.launch{
        runCatching{
            check(c.activeId==tab&&c.active?.url==url){bt(R.string.msg_381994932dcc)}
            check(replace||css.isNotBlank()||js.isNotBlank()||html.isNotBlank()){bt(R.string.msg_ac2471607490)}
            val edit=PageEdit(selector=selected.selector,css=css,js=js,html=html,mode=if(replace)"replace"else"append");edit.validate()
            c.validateSelectors(listOf(edit.selector))?.let{error(it)}
            check(original.edits.size<100){bt(R.string.msg_5d4fe5cb5542)}
            val value=original.copy(edits=original.edits+edit);c.validateCode(value);c.saveSite(value,reload=true);c.stopEye();c.sheet="";c.notice=bt(R.string.msg_9104269ef996)
        }.onFailure{error=it.localizedMessage.orEmpty()}
    }},modifier=Modifier.fillMaxWidth()){Text(if(loading)bt(R.string.msg_2261cfdbcec7)else bt(R.string.msg_d5ae259c7a80))}
}

@Composable internal fun CodeInput(label:String,value:String,onChange:(String)->Unit,tag:String,placeholder:String=""){
    OutlinedTextField(value,onChange,label={Text(label)},placeholder={Text(placeholder,fontSize=12.sp)},textStyle=androidx.compose.ui.text.TextStyle(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=18.sp),modifier=Modifier.fillMaxWidth().testTag(tag),minLines=3,maxLines=7)
}

@Composable internal fun DeveloperAiPanel(c:BrowserController,revision:RuleEditSession?=null,onSettings:()->Unit){
    c.revision
    val local=c.store.aiProvider=="gemma"
    var analysisJob by remember{mutableStateOf<Job?>(null)}
    val selected=remember{if(revision==null)c.aiElement else null};val original=remember{revision?.value()?:c.draft?:c.site};val tab=remember{c.activeId};val url=remember{c.active?.url}
    val scope=rememberCoroutineScope();var problem by remember{mutableStateOf("")};var consent by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var proposal by remember{mutableStateOf<DeveloperProposal?>(null)};var analyzedDocument by remember{mutableStateOf("")}
    Text(if(revision!=null)bt(R.string.msg_1dad1543f3e3)else if(selected==null)bt(R.string.msg_9cde4de25fb1)else bt(R.string.msg_76cd2d1a88a1),fontSize=20.sp)
    Text(if(revision!=null)bt(R.string.msg_20fa76a6bfbe ,original.domain,revision.edit?.selector?:bt(R.string.msg_b654f1e4475a))else if(selected==null)bt(R.string.msg_b243a787a694 ,original.domain)else bt(R.string.msg_ce9392b6fc9c ,selected.label,selected.selector),fontSize=13.sp,lineHeight=21.sp)
    OutlinedTextField(problem,{problem=it;proposal=null},enabled=!busy,label={Text(if(revision!=null)bt(R.string.msg_5dc364ad9d00)else if(selected==null)bt(R.string.msg_4df18c378546)else bt(R.string.msg_5f12497b6c2b))},modifier=Modifier.fillMaxWidth().testTag("developer-ai-problem"),minLines=3,maxLines=5)
    if(revision!=null)Text(bt(R.string.msg_53c859901fee ,if(local)"6,000"else"24,000"),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(selected!=null)Text(bt(R.string.msg_4f5d3e651cf5),fontSize=12.sp,lineHeight=19.sp)
    if(local){
        Text(bt(R.string.msg_cef6b9a5a9c1),fontSize=12.sp,lineHeight=19.sp)
        if(!c.gemma.ready)GemmaSetupPanel(c)
    }else{
        Text(if(revision!=null)bt(R.string.msg_b45dad973407)else bt(R.string.msg_fbd9319f336c),fontSize=12.sp,lineHeight=19.sp)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it},enabled=!busy);Text(bt(R.string.msg_87bc9921ff32),fontSize=13.sp)}
    }
    TextButton(onClick=onSettings){Text(bt(R.string.msg_ce48ba00d23c))}
    if(!local&&!c.store.hasKey())Button(onClick=onSettings){Text(bt(R.string.msg_74d0e7f413c2))}
    else Button(enabled=!busy&&(local||consent)&&problem.isNotBlank()&&(!local||c.gemma.ready),onClick={busy=true;error="";proposal=null;analysisJob=scope.launch{
        runCatching{
            check(c.activeId==tab&&c.active?.url==url){bt(R.string.msg_42ae55f2978a)}
            revision?.checkCurrent(c)
            analyzedDocument=c.js("performance.timeOrigin")
            val raw=if(revision!=null)c.js("window.__chengjingEye?.originalSource(${revision.edit?.selector?.let{JSONObject.quote(it)}?:"null"})")else c.js("window.__chengjingEye?.snapshot(${selected?.selector?.let{JSONObject.quote(it)}?:"null"})")
            check(raw!="null"){if(revision?.edit!=null)bt(R.string.msg_6be0d29202fe)else bt(R.string.msg_fe0a15f5444f)}
            val data=JSONArray("[$raw]").getString(0)
            check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){bt(R.string.msg_d1127956fd88)}
            if(revision!=null)c.developerRevision(problem,data,original,revision.editId)else c.developerSuggestion(problem,data,original,selected?.selector)
        }.onSuccess{proposal=it}.onFailure{error=if(it is CancellationException)bt(R.string.msg_5563d5c45cec)else it.localizedMessage.orEmpty()};busy=false
    }},modifier=Modifier.fillMaxWidth()){Text(if(busy)bt(R.string.msg_be9bf64f0d89)else if(local)bt(R.string.msg_d3e4febfceff)else bt(R.string.msg_10c558a33a0a))}
    if(analysisJob?.isActive==true)TextButton(onClick={if(local)c.gemma.cancelInference();analysisJob?.cancel()}){Text(bt(R.string.msg_a9fcfe87238e))}
    proposal?.let{p->
        SettingsGroup(bt(R.string.msg_e0a3643f8aa1)){
            Text(p.explanation,fontSize=14.sp,lineHeight=22.sp)
            if(p.changes.isEmpty())Text(bt(R.string.msg_f8236285164f))else p.changes.forEach{Text(it,fontSize=13.sp)}
            Text(bt(R.string.msg_dc34ed234d2d ,original.domain),fontSize=12.sp,lineHeight=19.sp)
            var code by remember{mutableStateOf(false)}
            TextButton(onClick={code=!code}){Text(if(code)bt(R.string.msg_6a087b05fdd4)else bt(R.string.msg_b43c7448f51e))}
            if(code)Text(p.result.json().toString(2),fontSize=11.sp,fontFamily=FontFamily.Monospace)
            Button(enabled=!busy&&p.changes.isNotEmpty(),onClick={busy=true;scope.launch{
                runCatching{
                    check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){bt(R.string.msg_d1127956fd88)}
                    check((c.draft?:c.site)==(revision?.original?:original)){bt(R.string.msg_ec874e7b427c)}
                    revision?.checkCurrent(c)
                    c.validateSelectors(p.selectors)?.let{error(it)}
                    c.validateCode(p.result)
                    check(c.activeId==tab&&c.active?.url==url&&c.js("performance.timeOrigin")==analyzedDocument){bt(R.string.msg_d1127956fd88)}
                    check((c.draft?:c.site)==(revision?.original?:original)){bt(R.string.msg_ec874e7b427c)}
                    revision?.checkCurrent(c)
                    c.saveSite(p.result,reload=true);c.stopEye();c.sheet="";c.notice=bt(R.string.msg_b879f4dea69c)
                }.onFailure{error=it.localizedMessage.orEmpty()};busy=false
            }},modifier=Modifier.fillMaxWidth()){Text(if(revision!=null)bt(R.string.msg_4522951c203c)else bt(R.string.msg_e0f0d077bf4d))}
            Text(bt(R.string.msg_a8bcdc2ed6f7),fontSize=12.sp)
        }
    }
    if(error.isNotEmpty())Text(error,modifier=Modifier.testTag("developer-error"),color=MaterialTheme.colorScheme.error)
}
