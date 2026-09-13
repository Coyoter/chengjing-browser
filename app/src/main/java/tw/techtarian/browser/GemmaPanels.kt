package tw.techtarian.browser
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AiProviderPanel(c:BrowserController){
    c.revision
    SettingsGroup("AI 執行方式"){
        listOf("gemma" to "Gemma 4 · 手機本機","openrouter" to "OpenRouter · 雲端模型").forEach{(id,label)->
            Row(Modifier.fillMaxWidth().clickable{c.store.aiProvider=id;c.revision++},verticalAlignment=Alignment.CenterVertically){RadioButton(c.store.aiProvider==id,{c.store.aiProvider=id;c.revision++});Text(label,fontSize=15.sp)}
        }
        Text("本機模式不需要 API Key；雲端模式可自行選擇服務商提供的模型。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun GemmaSetupPanel(c:BrowserController){
    val model=c.gemma
    var metered by remember{mutableStateOf(false)}
    SettingsGroup("Gemma 4 E2B"){
        Text(model.status,fontSize=15.sp)
        Text("初次需下載約 2.59 GB 模型，保留約 3 GB 儲存空間。需要 64 位元裝置，建議至少 6 GB 記憶體；分析時會使用手機電量。下載後，分析資料留在本機。",fontSize=12.sp,lineHeight=19.sp)
        if(model.downloading){
            LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={model.removeModel()}){Text("取消模型下載")}
        }else if(model.ready){
            Text("模型已完成完整性檢查，可離線使用。",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={model.removeModel()}){Text("移除模型以釋放空間")}
        }else{
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(metered,{metered=it});Text("允許使用行動網路下載",fontSize=13.sp)}
            Button(enabled=model.supported,onClick={model.startDownload(metered)},modifier=Modifier.fillMaxWidth().testTag("download-gemma")){Text("下載 Gemma 4 本機模型")}
            if(!model.supported)Text("此裝置不支援本機推論，可選用 OpenRouter。",fontSize=12.sp)
        }
        if(model.error.isNotBlank())Text(model.error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        Text("模型：Google Gemma 4，Apache 2.0。模型檔下載自 Google 文件連結的 LiteRT Community。",fontSize=11.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
