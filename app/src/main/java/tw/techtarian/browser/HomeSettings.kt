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
            else error=bt(R.string.msg_0cc2d828db73)
        }catch(_:Exception){error=bt(R.string.msg_1e32449f1b19)}
    }
    SettingsGroup(bt(R.string.msg_c907f8be4cbf)){
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f).padding(end=12.dp)){
                Text(bt(R.string.msg_d03a088f3260),fontSize=16.sp)
                Text(bt(R.string.msg_7ada4cb90804),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(home.enabled,{value->
                runCatching{home.updateEnabled(value)}.onFailure{c.notice=bt(R.string.msg_75f48b05acb2)}
                if(!value){choosingCustom=home.custom;draft=home.url;error=null;focus.clearFocus()}
            },modifier=Modifier.testTag("home-button-switch").semantics{contentDescription=bt(R.string.msg_d03a088f3260)})
        }
        if(home.enabled){
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
            HomeChoice(bt(R.string.msg_01e143a4ff67),!choosingCustom,"home-default-choice"){
                runCatching{home.useDefault()}.onSuccess{choosingCustom=false;error=null;focus.clearFocus()}.onFailure{c.notice=bt(R.string.msg_75f48b05acb2)}
            }
            HomeChoice(bt(R.string.msg_45285b97fd2f),choosingCustom,"home-custom-choice"){choosingCustom=true}
            if(choosingCustom){
                OutlinedTextField(value=draft,onValueChange={draft=it;error=null},label={Text(bt(R.string.msg_d7b21d1b7ecc))},
                    placeholder={Text("https://example.com")},modifier=Modifier.fillMaxWidth().testTag("home-url-input"),
                    singleLine=true,isError=error!=null,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={save()}))
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.testTag("home-url-error"))}
                Button(onClick={save()},enabled=draft.isNotBlank()&&(!home.custom||HomePolicy.normalizeUrl(draft)!=home.url),
                    modifier=Modifier.fillMaxWidth().testTag("save-home-url"),shape=RoundedCornerShape(14.dp)){
                    Text(if(home.custom&&HomePolicy.normalizeUrl(draft)==home.url)bt(R.string.msg_2a4c3223c02b)else bt(R.string.msg_0edd4290c301))
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
