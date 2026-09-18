package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun HomeSettings(c:BrowserController){
    val home=c.home
    var draft by remember{mutableStateOf(home.url)}
    var choosingCustom by remember{mutableStateOf(home.custom)}
    var error by remember{mutableStateOf<String?>(null)}
    val focus=LocalFocusManager.current
    fun save(){
        try{
            if(home.useCustom(draft)){draft=home.url;error=null;focus.clearFocus()}
            else error="請輸入有效的 HTTP 或 HTTPS 網址"
        }catch(_:Exception){error="儲存未完成，請稍後再試"}
    }
    SettingsGroup("首頁設定"){
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f).padding(end=12.dp)){
                Text("顯示首頁按鈕",fontSize=16.sp)
                Text("放在網址列前方",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(home.enabled,{value->
                runCatching{home.updateEnabled(value)}.onFailure{c.notice="首頁設定未能儲存"}
                if(!value){choosingCustom=home.custom;draft=home.url;error=null;focus.clearFocus()}
            },modifier=Modifier.testTag("home-button-switch").semantics{contentDescription="顯示首頁按鈕"})
        }
        if(home.enabled){
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
            HomeChoice("澄境首頁",!choosingCustom,"home-default-choice"){
                runCatching{home.useDefault()}.onSuccess{choosingCustom=false;error=null;focus.clearFocus()}.onFailure{c.notice="首頁設定未能儲存"}
            }
            HomeChoice("自訂網址",choosingCustom,"home-custom-choice"){choosingCustom=true}
            if(choosingCustom){
                OutlinedTextField(value=draft,onValueChange={draft=it;error=null},label={Text("首頁網址")},
                    placeholder={Text("https://example.com")},modifier=Modifier.fillMaxWidth().testTag("home-url-input"),
                    singleLine=true,isError=error!=null,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={save()}))
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.testTag("home-url-error"))}
                Button(onClick={save()},enabled=draft.isNotBlank()&&(!home.custom||HomePolicy.normalizeUrl(draft)!=home.url),
                    modifier=Modifier.fillMaxWidth().testTag("save-home-url"),shape=RoundedCornerShape(14.dp)){
                    Text(if(home.custom&&HomePolicy.normalizeUrl(draft)==home.url)"已儲存"else"儲存首頁")
                }
            }
        }
    }
}
@Composable private fun HomeChoice(label:String,selected:Boolean,tag:String,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag).selectable(selected=selected,role=Role.RadioButton,onClick=onClick),verticalAlignment=Alignment.CenterVertically){
        RadioButton(selected,onClick=null);Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f),fontSize=15.sp)
    }
}
