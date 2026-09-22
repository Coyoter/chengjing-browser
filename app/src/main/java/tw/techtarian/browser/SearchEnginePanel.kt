package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun SearchEnginePanel(c:BrowserController){
    val store=c.store
    val current=store.searchSettings
    var selected by remember{mutableStateOf(current.engineId)}
    var custom by remember{mutableStateOf(store.customSearchTemplate)}
    var error by remember{mutableStateOf<String?>(null)}
    val focus=LocalFocusManager.current
    fun saveCustom(){
        val problem=SearchEngines.templateError(custom)
        if(problem!=null){error=problem;return}
        runCatching{store.useCustomSearch(custom)}
            .onSuccess{selected="custom";custom=store.customSearchTemplate;error=null;focus.clearFocus();c.notice="已改用自訂搜尋引擎"}
            .onFailure{error=it.localizedMessage?:"搜尋引擎設定未能儲存"}
    }
    Surface(Modifier.fillMaxWidth().testTag("search-engine-summary"),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer){
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(40.dp).clip(CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Search,null,tint=MaterialTheme.colorScheme.primary)}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text("目前使用",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SearchEngines.label(current),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text(SearchEngines.description(current),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    Text("只有送出網址列內容時，搜尋文字才會傳給你選擇的服務。網址與本機建議的判斷方式不變。",fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsGroup("選擇搜尋引擎"){
        SearchEngines.builtIns.forEach{engine->
            SearchEngineChoice(engine.label,engine.description,selected==engine.id,"search-engine:${engine.id}"){
                runCatching{store.useSearchEngine(engine.id)}
                    .onSuccess{selected=engine.id;error=null;focus.clearFocus();c.notice="已改用 ${engine.label}"}
                    .onFailure{error=it.localizedMessage?:"搜尋引擎設定未能儲存"}
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
        SearchEngineChoice("自訂","使用含有 {query} 的 HTTPS 網址範本",selected=="custom","search-engine:custom"){
            selected="custom";error=null
            if(store.customSearchTemplate.isNotBlank())runCatching{store.useCustomSearch(store.customSearchTemplate)}
        }
        if(selected=="custom"){
            val problem=SearchEngines.templateError(custom)
            OutlinedTextField(
                value=custom,onValueChange={custom=it;error=null},singleLine=false,minLines=2,maxLines=3,isError=custom.isNotBlank()&&problem!=null,
                modifier=Modifier.fillMaxWidth().testTag("custom-search-template"),
                label={Text("搜尋網址範本")},placeholder={Text("https://example.com/search?q={query}")},
                supportingText={Text(error?:problem?.takeIf{custom.isNotBlank()}?:"{query} 會換成已安全編碼的搜尋文字",modifier=Modifier.testTag("custom-search-guidance"))},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={saveCustom()})
            )
            if(problem==null){
                val preview=SearchEngines.searchUrl("澄境 瀏覽器",SearchSettings("custom",custom))
                Text("預覽：$preview",fontSize=11.sp,lineHeight=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("custom-search-preview"))
            }
            Button(onClick={saveCustom()},enabled=problem==null,modifier=Modifier.fillMaxWidth().testTag("save-custom-search"),shape=RoundedCornerShape(14.dp)){Text("儲存並使用")}
        }
    }
}

@Composable private fun SearchEngineChoice(label:String,description:String,selected:Boolean,tag:String,onClick:()->Unit){
    Row(
        Modifier.fillMaxWidth().heightIn(min=58.dp).clip(RoundedCornerShape(12.dp))
            .selectable(selected=selected,role=Role.RadioButton,onClick=onClick).testTag(tag).padding(horizontal=4.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        RadioButton(selected=selected,onClick=null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(label,fontSize=15.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
            Text(description,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
