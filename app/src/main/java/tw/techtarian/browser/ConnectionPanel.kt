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
    Text(primary?:page.ifBlank{"尚未開啟網站"},fontSize=16.sp)
    if(tab?.certificateWarning?.isNotBlank()==true)Text(tab.certificateWarning,color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=20.sp)
    else Text(if(primary!=null)"目前使用 HTTPS 加密連線。"else if(page.startsWith("http://"))"HTTP 連線未加密，沒有可驗證的網站憑證。"else"先開啟網站，即可查看連線資訊。",fontSize=13.sp)
    val origins=(listOfNotNull(primary)+tab?.warnings?.originsFor(page).orEmpty()).distinct()
    origins.forEach{origin->
        val enabled=c.store.certificateException(origin)
        SettingsGroup(if(origin==primary)"目前網站"else"頁面使用的其他來源"){
            Text(origin,fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("允許憑證例外",fontSize=15.sp)
                    Text(if(enabled)"已開啟；重新啟動後仍會保留"else"預設關閉，驗證失敗時停止連線",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled,{value->if(value)confirming=origin else runCatching{c.setCertificateException(origin,false)}.onFailure{error=it.localizedMessage.orEmpty()}},Modifier.testTag("certificate-exception:$origin"))
            }
            Text("只套用此 HTTPS 主機名稱（包含其不同連接埠），不包含其他子網域或第三方來源。你關閉前會持續保留。",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
    Text("憑證例外會降低網站身分驗證保障；它無法修復伺服器本身無法建立加密連線的問題。",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    confirming?.let{origin->
        AlertDialog(onDismissRequest={confirming=null},title={Text("允許這個網站的憑證例外？")},text={Text("$origin\n\n這個設定會永久保留，直到你關閉。即使網站憑證失效或身分無法驗證，仍可能載入；登入資訊與輸入內容可能被非預期的對象取得。請只在你了解並接受風險時開啟。")},confirmButton={TextButton(onClick={runCatching{c.setCertificateException(origin,true)}.onSuccess{confirming=null}.onFailure{error=it.localizedMessage.orEmpty();confirming=null}}){Text("我了解，允許此網站")}},dismissButton={TextButton(onClick={confirming=null}){Text("保持驗證")}})
    }
}
