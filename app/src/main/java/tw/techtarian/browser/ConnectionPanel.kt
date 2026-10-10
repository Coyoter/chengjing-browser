package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp

@Composable internal fun ConnectionPanel(c:BrowserController){
    val tab=c.active;val page=tab?.url.orEmpty();val primary=CertificateExceptions.site(page)
    c.revision
    var confirming by remember{mutableStateOf<String?>(null)}
    var error by remember{mutableStateOf("")}
    Text(primary?:page.ifBlank{bt(R.string.msg_f215d1bff15a)},fontSize=16.sp)
    if(tab?.certificateWarning?.isNotBlank()==true)Text(tab.certificateWarning,color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=20.sp)
    else Text(if(primary!=null)bt(R.string.msg_ee33611e6daa)else if(page.startsWith("http://"))bt(R.string.msg_c6905e1adffd)else bt(R.string.msg_87178db80ada),fontSize=13.sp)
    LocationPanel(c)
    WebsitePermissionsPanel(c)
    val origins=(listOfNotNull(primary)+(c.active?.warnings?:CertificateWarnings.session).originsFor(page)).distinct()
    origins.forEach{origin->
        val enabled=c.store.certificateException(origin)
        SettingsGroup(if(origin==primary)bt(R.string.msg_3e26c53efe37)else bt(R.string.msg_9c5784103466)){
            Text(origin,fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(bt(R.string.msg_a1c2a2e33727),fontSize=15.sp)
                    Text(if(enabled)bt(R.string.msg_e5cf1ccb2b44)else bt(R.string.msg_2307cd3ce0fd),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled,{value->if(value)confirming=origin else runCatching{c.setCertificateException(origin,false)}.onFailure{error=it.localizedMessage.orEmpty()}},Modifier.testTag("certificate-exception:$origin"))
            }
            Text(bt(R.string.msg_01c30bea5f57),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
    Text(bt(R.string.msg_f31de5ba2b03),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    confirming?.let{origin->
        AlertDialog(onDismissRequest={confirming=null},title={Text(bt(R.string.msg_3a6a1d3ef14e))},text={Text(bt(R.string.msg_41a1ffa83399 ,origin))},confirmButton={TextButton(onClick={runCatching{c.setCertificateException(origin,true)}.onSuccess{confirming=null}.onFailure{error=it.localizedMessage.orEmpty();confirming=null}}){Text(bt(R.string.msg_d848bdedee10))}},dismissButton={TextButton(onClick={confirming=null}){Text(bt(R.string.msg_c58f37d7a445))}})
    }
}
