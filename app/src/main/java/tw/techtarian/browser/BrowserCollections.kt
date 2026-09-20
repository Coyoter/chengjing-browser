package tw.techtarian.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

internal data class DownloadItem(val id:Long,val title:String,val source:String,val mime:String,val status:Int,val done:Long,val total:Long)
internal class BrowserDownloads(private val context:Context) {
    private val manager=context.getSystemService(DownloadManager::class.java)
    // DownloadManager scopes this query to our UID, including pre-upgrade downloads.
    fun list():List<DownloadItem> {
        val result=mutableListOf<DownloadItem>()
        manager.query(DownloadManager.Query())?.use{cursor->
            fun text(column:String)=cursor.getString(cursor.getColumnIndexOrThrow(column)).orEmpty()
            fun number(column:String)=cursor.getLong(cursor.getColumnIndexOrThrow(column))
            while(cursor.moveToNext()) {
                val local=text(DownloadManager.COLUMN_LOCAL_URI)
                val title=text(DownloadManager.COLUMN_TITLE)
                if(local.contains("/Android/data/${context.packageName}/")||title=="澄境 · Gemma 4 本機模型")continue
                result.add(DownloadItem(number(DownloadManager.COLUMN_ID),title.ifBlank{"未命名檔案"},
                    text(DownloadManager.COLUMN_URI),text(DownloadManager.COLUMN_MEDIA_TYPE),
                    number(DownloadManager.COLUMN_STATUS).toInt(),number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
            }
        }
        return result.sortedByDescending{it.id}
    }
    fun open(item:DownloadItem):String?=try {
        val uri=manager.getUriForDownloadedFile(item.id)?:error("下載尚未完成或檔案已移除")
        context.contentResolver.openFileDescriptor(uri,"r")?.use{}?:error("檔案已移除")
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,item.mime.ifBlank{"*/*"})
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent,"開啟下載檔案").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }catch(_:android.content.ActivityNotFoundException){"沒有可開啟此檔案的應用程式"}
    catch(_:Exception){"檔案無法開啟，可能已被移動或刪除"}
}

@Composable internal fun ColumnScope.DownloadsScreen(c:BrowserController) {
    val downloads=remember{BrowserDownloads(c.context)}
    var rows by remember{mutableStateOf<List<DownloadItem>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var query by remember{mutableStateOf("")}
    var openError by remember{mutableStateOf<String?>(null)}
    val lifecycle=(c.context as MainActivity).lifecycle
    LaunchedEffect(downloads,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                runCatching{withContext(Dispatchers.IO){downloads.list()}}.onSuccess{rows=it;error=""}
                    .onFailure{error="暫時無法讀取系統下載，請稍後重試"}
                loading=false
                delay(1800)
            }
        }
    }
    CollectionHeader("下載", "只顯示由澄境瀏覽器交給系統處理的下載。", query,{query=it})
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))
    if(error.isNotEmpty())Text(error,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)
    val filtered=rows.filter{it.title.contains(query,true)||it.source.contains(query,true)}
    if(!loading&&filtered.isEmpty())CollectionEmpty(if(query.isEmpty())"還沒有下載項目"else"找不到相符的下載", "長按網頁圖片可下載；網站提供的檔案下載也會出現在這裡。")
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("download-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(filtered,key={it.id}){item->
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(item.mime.startsWith("image/"))Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                            Text(Domains.scope(item.source),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        if(item.status==DownloadManager.STATUS_SUCCESSFUL)IconButton(onClick={openError=downloads.open(item)}){Icon(Icons.Outlined.OpenInNew,"開啟 ${item.title}")}
                    }
                    val status=when(item.status){DownloadManager.STATUS_SUCCESSFUL->"已完成";DownloadManager.STATUS_FAILED->"下載失敗";DownloadManager.STATUS_PAUSED->"已暫停，等待系統重試";DownloadManager.STATUS_RUNNING->"下載中";else->"等待下載"}
                    val size=if(item.total>0)Formatter.formatFileSize(c.context,item.total)else if(item.done>0)Formatter.formatFileSize(c.context,item.done)else"大小待確認"
                    Text("$status · $size",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(item.status==DownloadManager.STATUS_RUNNING) {
                        if(item.total>0)LinearProgressIndicator(progress={(item.done.toFloat()/item.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    openError?.let{message->AlertDialog(onDismissRequest={openError=null},title={Text("下載檔案")},text={Text(message)},confirmButton={TextButton(onClick={openError=null}){Text("知道了")}})}
}

@Composable internal fun ColumnScope.HistoryScreen(c:BrowserController) {
    var query by remember{mutableStateOf("")}
    var revision by remember{mutableIntStateOf(0)}
    var clear by remember{mutableStateOf(false)}
    val rows=remember(revision,c.revision){c.store.history()}
    CollectionHeader("瀏覽記錄","最近 250 個頁面，只保留在這支手機。無痕瀏覽不會加入。",query,{query=it})
    if(rows.isNotEmpty())TextButton(onClick={clear=true},modifier=Modifier.padding(horizontal=12.dp)){Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("清除瀏覽記錄")}
    val filtered=rows.filter{it.first.contains(query,true)||it.second.contains(query,true)}
    if(filtered.isEmpty())CollectionEmpty(if(query.isEmpty())"尚無瀏覽記錄"else"找不到相符的頁面","一般分頁瀏覽過的頁面會出現在這裡。")
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("history-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        items(filtered,key={it.first}){(url,title)->
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).clickable{c.navigate(url)}.padding(14.dp)) {
                        Text(title.ifBlank{url},maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(url,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={c.store.removeHistory(url);revision++;c.revision++}){Icon(Icons.Outlined.Close,"移除此筆瀏覽記錄")}
                }
            }
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清除瀏覽記錄？")},text={Text("將清除本機瀏覽與搜尋記錄，不會刪除書籤、收藏或網站登入資料。")},confirmButton={TextButton(onClick={c.store.clearHistory();revision++;c.revision++;clear=false}){Text("清除")}},dismissButton={TextButton(onClick={clear=false}){Text("取消")}})
}

@Composable private fun CollectionHeader(title:String,description:String,query:String,onQuery:(String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(description,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query,onQuery,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("搜尋$title")},leadingIcon={Icon(Icons.Outlined.Search,null)},shape=RoundedCornerShape(16.dp))
    }
}
@Composable internal fun CollectionEmpty(title:String,description:String) {
    Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
