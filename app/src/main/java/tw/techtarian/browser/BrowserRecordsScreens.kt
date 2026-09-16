package tw.techtarian.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class BrowserDownload(val id:Long,val title:String,val bytes:Long,val total:Long,val status:Int,val modified:Long)

/** The normal DownloadManager Query is scoped to this app's downloads, including
 * downloads enqueued by previous versions; it does not scan other apps' files.
 */
internal class DownloadLibrary(private val context:Context){
    private val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    fun list():List<BrowserDownload>{
        val result=mutableListOf<BrowserDownload>()
        manager.query(DownloadManager.Query()).use{cursor->
            check(cursor!=null){"系統下載服務沒有回傳資料"}
            fun number(column:String)=cursor.getLong(cursor.getColumnIndexOrThrow(column))
            while(cursor.moveToNext()){
                result.add(BrowserDownload(number(DownloadManager.COLUMN_ID),cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank{"未命名下載"},number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),number(DownloadManager.COLUMN_STATUS).toInt(),number(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)))
            }
        }
        return result.sortedByDescending{it.modified}
    }
    fun open(id:Long){
        val uri=manager.getUriForDownloadedFile(id)?:error("找不到已下載的檔案，可能已移動或刪除")
        context.contentResolver.openFileDescriptor(uri,"r")?.use{}?:error("找不到下載檔案")
        val intent=Intent(Intent.ACTION_VIEW).apply{
            setDataAndType(uri,manager.getMimeTypeForDownloadedFile(id)?:"application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=ClipData.newRawUri("下載檔案",uri)
        }
        context.startActivity(Intent.createChooser(intent,"開啟下載檔案"))
    }
}

@Composable internal fun DownloadsScreen(c:BrowserController){
    val library=remember(c){DownloadLibrary(c.context)}
    var rows by remember{mutableStateOf<List<BrowserDownload>>(emptyList())}
    var error by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(true)}
    var query by remember{mutableStateOf("")}
    var openError by remember{mutableStateOf("")}
    LaunchedEffect(library){
        (c.context as ComponentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){
                val result=withContext(Dispatchers.IO){runCatching{library.list()}}
                result.onSuccess{rows=it;error=""}.onFailure{error="目前無法讀取系統下載，請稍後重試"}
                loading=false
                delay(1800)
            }
        }
    }
    BrowserPanel(c){
        PanelHeader(c){c.sheet="menu"}
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text("由澄境瀏覽器下載的檔案",style=MaterialTheme.typography.titleMedium)
            Text("包含先前版本的系統下載。點選完成的項目即可開啟；手動下載的檔案不因關閉無痕分頁而刪除。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            RecordSearch(query,{query=it},"搜尋檔名","download-search")
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            val filtered=rows.filter{it.title.contains(query,true)}
            if(!loading&&filtered.isEmpty()&&error.isEmpty())RecordEmpty(if(query.isBlank())"尚無下載項目"else"找不到符合的檔案",if(query.isBlank())"長按網頁中的圖片，或點選網站下載連結。"else"試試其他關鍵字。")
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("download-list"),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
                items(filtered,key={it.id}){row->
                    val done=row.status==DownloadManager.STATUS_SUCCESSFUL
                    Surface(onClick={if(done)runCatching{library.open(row.id)}.onFailure{openError="無法開啟檔案：檔案可能已移動，或沒有可處理此格式的應用程式"}},enabled=done,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("download:${row.id}")){
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                            Icon(if(done)Icons.Outlined.Description else Icons.Outlined.Downloading,null,tint=MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text(row.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                                val status=when(row.status){DownloadManager.STATUS_SUCCESSFUL->"已完成";DownloadManager.STATUS_FAILED->"下載失敗";DownloadManager.STATUS_PAUSED->"等待連線／系統暫停";DownloadManager.STATUS_PENDING->"等待下載";else->"下載中"}
                                val bytes=Formatter.formatShortFileSize(c.context,row.bytes.coerceAtLeast(0))
                                val total=if(row.total>0)" / ${Formatter.formatShortFileSize(c.context,row.total)}"else""
                                Text("$status · $bytes$total",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(row.status==DownloadManager.STATUS_RUNNING){
                                    if(row.total>0)LinearProgressIndicator(progress={(row.bytes.toFloat()/row.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                            if(done)Icon(Icons.AutoMirrored.Outlined.OpenInNew,"開啟檔案",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if(openError.isNotEmpty())AlertDialog(onDismissRequest={openError=""},title={Text("下載檔案")},text={Text(openError)},confirmButton={TextButton(onClick={openError=""}){Text("知道了")}})
}

@Composable internal fun HistoryScreen(c:BrowserController){
    c.revision
    var query by remember{mutableStateOf("")}
    var clear by remember{mutableStateOf(false)}
    val entries=c.store.historyEntries()
    val filtered=entries.filter{it.title.contains(query,true)||it.url.contains(query,true)}
    val format=remember{SimpleDateFormat("yyyy/MM/dd",Locale.TAIWAN)}
    BrowserPanel(c){
        PanelHeader(c){c.sheet="menu"}
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("回到看過的頁面",style=MaterialTheme.typography.titleMedium)
                    Text("最近 250 個網址 · 只保存在這支手機",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(entries.isNotEmpty())TextButton(onClick={clear=true},modifier=Modifier.testTag("clear-history")){Text("清除")}
            }
            RecordSearch(query,{query=it},"搜尋標題或網址","history-search")
            if(filtered.isEmpty())RecordEmpty(if(query.isBlank())"還沒有歷史記錄"else"找不到符合的頁面","無痕瀏覽不會新增歷史記錄。")
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history-list"),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)){
                filtered.groupBy{if(it.visited>0)format.format(Date(it.visited))else"較早的紀錄"}.forEach{(day,rows)->
                    item(key="date:$day"){Text(day,Modifier.padding(top=10.dp,bottom=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    items(rows,key={it.url}){row->
                        Surface(onClick={c.navigate(row.url)},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
                            Row(Modifier.padding(start=16.dp,top=8.dp,bottom=8.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                    Text(row.title.ifBlank{row.url},maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
                                    Text(Domains.scope(row.url),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick={c.store.removeHistory(row.url);c.revision++}){Icon(Icons.Outlined.Close,"移除這筆歷史記錄",Modifier.size(18.dp))}
                            }
                        }
                    }
                }
            }
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清除歷史記錄？")},text={Text("會清除本機的瀏覽與搜尋紀錄，不刪除書籤、收藏、下載檔案或網站登入資料。")},confirmButton={TextButton(onClick={c.store.clearHistory();c.revision++;clear=false}){Text("清除紀錄")}},dismissButton={TextButton(onClick={clear=false}){Text("取消")}})
}

@Composable internal fun RecordSearch(value:String,onChange:(String)->Unit,label:String,tag:String){
    OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth().testTag(tag),singleLine=true,label={Text(label)},leadingIcon={Icon(Icons.Outlined.Search,null)},shape=RoundedCornerShape(14.dp))
}
@Composable internal fun RecordEmpty(title:String,description:String){
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
