package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable internal fun EngineVersionPanel(c:BrowserController){
    val installed=remember{ChromiumVersions.installed(c.context)}
    val repository=remember{EngineVersionRepository(c.context,installed.platform)}
    var stable by remember{mutableStateOf(repository.cached())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    suspend fun check(){
        busy=true;error=""
        try{stable=repository.refresh()}catch(e:CancellationException){throw e}catch(_:Exception){error="暫時查不到官方版本，請稍後重試。"}finally{busy=false}
    }
    LaunchedEffect(Unit){if(stable==null||System.currentTimeMillis()-stable!!.checkedAt !in 0L..21_600_000L)check()}
    SettingsGroup("瀏覽核心"){
        Row(Modifier.fillMaxWidth().testTag("engine-version-comparison"),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("目前 Chromium" to installed.version,"最新正式版" to (stable?.version?:if(busy)"查詢中…"else if(error.isNotEmpty())"暫無資料"else"尚未查詢")).forEachIndexed{index,(label,value)->
                Surface(Modifier.weight(1f),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value,Modifier.testTag(if(index==0)"engine-current"else"engine-stable"),fontFamily=FontFamily.Monospace,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text("目前由 ${installed.provider} 提供；對照 Google 公布的 ${if(installed.platform=="webview")"Android WebView"else"Android Chrome"} 正式版。",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        stable?.let{version->
            val compared=ChromiumVersions.compare(installed.version,version.version)
            Text(when{compared==null->"目前版本無法直接比較";compared<0->"官方正式版較新，可檢查核心更新";compared==0->"目前核心與官方正式版相同";else->"目前核心版本較新"},fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
            Text("${if(error.isNotEmpty())"保留上次結果 · "else""}查詢時間："+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(version.checkedAt)),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(error.isNotEmpty())Text(error,fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(enabled=!busy,onClick={scope.launch{check()}}){Text(if(busy)"正在查詢…"else"重新檢查版本",fontSize=12.sp)}
            TextButton(enabled=installed.packageName.isNotEmpty(),onClick={runCatching{openEngineUpdate(c.context,installed)}.onFailure{error="無法開啟更新頁，請在系統設定查看 WebView。"}}){Text("開啟核心更新頁",fontSize=12.sp)}
        }
        Text("更新由系統或商店提供，可能分批推出。",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
