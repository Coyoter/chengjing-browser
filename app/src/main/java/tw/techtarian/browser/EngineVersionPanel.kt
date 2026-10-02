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
        try{stable=repository.refresh()}catch(e:CancellationException){throw e}catch(_:Exception){error=bt(R.string.msg_e19c932091a7)}finally{busy=false}
    }
    LaunchedEffect(Unit){if(stable==null||System.currentTimeMillis()-stable!!.checkedAt !in 0L..21_600_000L)check()}
    SettingsGroup(bt(R.string.msg_85284e05ae9e)){
        Row(Modifier.fillMaxWidth().testTag("engine-version-comparison"),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(bt(R.string.msg_08a4dcb36197) to installed.version,bt(R.string.msg_870241d98eb8) to (stable?.version?:if(busy)bt(R.string.msg_b8bf2f806c91)else if(error.isNotEmpty())bt(R.string.msg_fb27d9cf6a63)else bt(R.string.msg_8b9b22849066))).forEachIndexed{index,(label,value)->
                Surface(Modifier.weight(1f),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value,Modifier.testTag(if(index==0)"engine-current"else"engine-stable"),fontFamily=FontFamily.Monospace,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text(bt(R.string.msg_1fe80e799fd9 ,installed.provider,if(installed.platform=="webview")"Android WebView"else"Android Chrome"),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        stable?.let{version->
            val compared=ChromiumVersions.compare(installed.version,version.version)
            Text(when{compared==null->bt(R.string.msg_e5d3e9548003);compared<0->bt(R.string.msg_72b9cc4cb174);compared==0->bt(R.string.msg_de1fbbd2f6c0);else->bt(R.string.msg_9b349d42dab8)},fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
            Text(bt(R.string.msg_a616b4a608fc ,if(error.isNotEmpty())bt(R.string.msg_43f5b26eb0ca)else"")+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(version.checkedAt)),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(error.isNotEmpty())Text(error,fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(enabled=!busy,onClick={scope.launch{check()}}){Text(if(busy)bt(R.string.msg_4f521b4e22a0)else bt(R.string.msg_73d3eac9c403),fontSize=12.sp)}
            TextButton(enabled=installed.packageName.isNotEmpty(),onClick={runCatching{openEngineUpdate(c.context,installed)}.onFailure{error=bt(R.string.msg_ff0bbcba22df)}}){Text(bt(R.string.msg_243aeef7919a),fontSize=12.sp)}
        }
        Text(bt(R.string.msg_eb5b5c578249),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
