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
    SettingsGroup(bt(R.string.msg_70ba6da30f88)){
        listOf("gemma" to bt(R.string.msg_5e770f2435f5),"openrouter" to bt(R.string.msg_c8f05005fd37)).forEach{(id,label)->
            Row(Modifier.fillMaxWidth().clickable{c.store.aiProvider=id;c.revision++},verticalAlignment=Alignment.CenterVertically){RadioButton(c.store.aiProvider==id,{c.store.aiProvider=id;c.revision++});Text(label,fontSize=15.sp)}
        }
        Text(bt(R.string.msg_7397e02a13de),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun GemmaSetupPanel(c:BrowserController){
    val model=c.gemma
    var metered by remember{mutableStateOf(false)}
    SettingsGroup("Gemma 4 E2B"){
        Text(model.status,fontSize=15.sp)
        Text(bt(R.string.msg_770c4bcb536a),fontSize=12.sp,lineHeight=19.sp)
        if(model.downloading){
            LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={model.removeModel()}){Text(bt(R.string.msg_0e9aa6d873a0))}
        }else if(model.ready){
            Text(bt(R.string.msg_d2cbf779f4a2),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={model.removeModel()}){Text(bt(R.string.msg_1300b82064c4))}
        }else{
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(metered,{metered=it});Text(bt(R.string.msg_28b9d535ae3d),fontSize=13.sp)}
            Button(enabled=model.supported,onClick={model.startDownload(metered)},modifier=Modifier.fillMaxWidth().testTag("download-gemma")){Text(bt(R.string.msg_fe9c98caa8a4))}
            if(!model.supported)Text(bt(R.string.msg_4d8ae165e6e6),fontSize=12.sp)
        }
        if(model.error.isNotBlank())Text(model.error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        Text(bt(R.string.msg_6cbc25f77915),fontSize=11.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
