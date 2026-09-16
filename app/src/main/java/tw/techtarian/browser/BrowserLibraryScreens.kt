package tw.techtarian.browser

import android.app.DownloadManager
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable internal fun BrowserCollectionScreen(c:BrowserController) {
    BrowserPanel(c){
        PanelHeader(c){c.sheet=if(c.sheet=="tabs")"" else "menu"}
        when(c.sheet){
            "tabs"->TabCollection(c)
            "history"->HistoryCollection(c)
            "downloads"->DownloadCollection(c)
        }
    }
}

@Composable private fun ColumnScope.TabCollection(c:BrowserController) {
    val colors=MaterialTheme.colorScheme
    val privateGroup=c.tabGroupPrivate
    val rows=c.tabs.filter{it.incognito==privateGroup}
    var closeAll by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(false,true).forEach{privateMode->
            val chosen=privateMode==privateGroup
            Surface(onClick={c.tabGroupPrivate=privateMode},modifier=Modifier.weight(1f).height(48.dp)
                .testTag(if(privateMode)"private-tab-group" else "normal-tab-group")
                .semantics{selected=chosen;role=Role.Tab},shape=RoundedCornerShape(14.dp),
                color=if(chosen)colors.primaryContainer else colors.surface){
                Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    Icon(if(privateMode)Icons.Outlined.Shield else Icons.Outlined.Tab,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${if(privateMode)"無痕" else "一般"} · ${c.tabs.count{it.incognito==privateMode}}",fontSize=14.sp,fontWeight=FontWeight.Medium)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start=24.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically){
        Text(if(privateGroup)"關閉所有無痕分頁後清除網站資料" else "分頁預覽只暫存在記憶體",Modifier.weight(1f),fontSize=12.sp,color=colors.onSurfaceVariant)
        TextButton(onClick={closeAll=true},enabled=rows.isNotEmpty()){Text("全部關閉",fontSize=12.sp)}
    }
    if(rows.isEmpty()){
        Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Icon(if(privateGroup)Icons.Outlined.Shield else Icons.Outlined.Tab,null,Modifier.size(40.dp),tint=colors.primary)
            Spacer(Modifier.height(16.dp));Text(if(privateGroup)"留一段不記錄的瀏覽時間" else "從一個新分頁開始",style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp));Text(if(privateGroup)"不保存歷史與搜尋詞；網站仍能看到你的 IP 位址。下載檔案需另行確認保留。" else "開啟的網頁會整理在這裡。",fontSize=13.sp,lineHeight=21.sp,color=colors.onSurfaceVariant)
        }
    }else LazyVerticalGrid(GridCells.Adaptive(148.dp),Modifier.weight(1f).testTag("tab-card-grid"),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        items(rows,key={it.id}){tab->TabCard(c,tab)}
    }
    Button(onClick={if(c.newTab(incognito=privateGroup)!=null)c.sheet=""},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).height(50.dp).testTag("new-group-tab")){
        Icon(Icons.Outlined.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(privateGroup)"新增無痕分頁" else "新增一般分頁")
    }
    if(closeAll)AlertDialog(onDismissRequest={closeAll=false},title={Text(if(privateGroup)"關閉全部無痕分頁？" else "關閉全部一般分頁？")},
        text={Text(if(privateGroup)"網站登入及暫存資料會清除，無法復原這些分頁。已確認下載的檔案會保留。" else "將關閉這一組的 ${rows.size} 個分頁，不影響另一組分頁或已儲存的書籤。")},
        confirmButton={TextButton(onClick={c.closeGroup(privateGroup);closeAll=false}){Text("全部關閉")}},dismissButton={TextButton(onClick={closeAll=false}){Text("取消")}})
}

@Composable private fun TabCard(c:BrowserController,tab:BrowserTab) {
    val colors=MaterialTheme.colorScheme
    val current=tab.id==c.activeId
    Surface(Modifier.fillMaxWidth().testTag("tab-card:${tab.id}"),shape=RoundedCornerShape(18.dp),color=colors.surface,
        border=BorderStroke(if(current)2.dp else 1.dp,if(current)colors.primary else colors.outlineVariant)){
        Column{
            Box(Modifier.fillMaxWidth().height(120.dp)){
                Surface(onClick={c.switchTab(tab.id)},modifier=Modifier.fillMaxSize().testTag("tab-open:${tab.id}"),color=colors.surfaceVariant){
                    val preview=tab.preview.takeUnless{tab.incognito}
                    if(preview!=null)Image(preview.asImageBitmap(),"${tab.title}的預覽",Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alignment=Alignment.TopCenter)
                    else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(if(tab.incognito)Icons.Outlined.Shield else Icons.Outlined.Language,null,Modifier.size(32.dp),tint=colors.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp));Text(if(tab.incognito)"不保存網頁預覽" else if(tab.url.isBlank())"澄境首頁" else Domains.scope(tab.url),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick={c.closeTab(tab.id)},Modifier.align(Alignment.TopEnd).size(48.dp).testTag("tab-close:${tab.id}")){
                    Surface(shape=RoundedCornerShape(20.dp),color=colors.surface.copy(alpha=.94f)){
                        Icon(Icons.Outlined.Close,"關閉 ${tab.title}",Modifier.padding(5.dp).size(18.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clickable{c.switchTab(tab.id)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(tab.title.ifBlank{"新分頁"},fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(current)"目前分頁" else Domains.scope(tab.url).ifBlank{if(tab.incognito)"無痕分頁" else "新分頁"},fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(current)colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun PrivateHome() {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center){
        Icon(Icons.Outlined.Shield,null,Modifier.size(42.dp),tint=colors.primary)
        Spacer(Modifier.height(24.dp));Text("這段瀏覽，不留在歷史裡。",fontSize=28.sp,lineHeight=38.sp,fontWeight=FontWeight.Light)
        Spacer(Modifier.height(18.dp));Text("無痕分頁與一般分頁分開登入，不保存瀏覽歷史、搜尋詞或分頁預覽。關閉全部無痕分頁或重新啟動應用程式後，清除這段工作階段的網站資料。",fontSize=14.sp,lineHeight=23.sp,color=colors.onSurfaceVariant)
        Spacer(Modifier.height(14.dp));Text("無痕不是匿名工具。網站、網路管理者或網路業者仍可能知道你的活動；經你確認的下載檔案與分享內容仍會保留。",fontSize=12.sp,lineHeight=20.sp,color=colors.onSurfaceVariant)
    }
}

@Composable private fun ColumnScope.HistoryCollection(c:BrowserController) {
    var search by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    c.revision
    val rows=c.store.historyEntries().filter{it.title.contains(search,true)||it.url.contains(search,true)}
    Text("最近 250 個不同頁面 · 無痕瀏覽不記錄",Modifier.padding(horizontal=24.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
        OutlinedTextField(search,{search=it},Modifier.weight(1f).testTag("history-search"),singleLine=true,label={Text("搜尋歷史記錄")},leadingIcon={Icon(Icons.Outlined.Search,null)})
        TextButton(onClick={confirm=true},enabled=c.store.historyEntries().isNotEmpty()){Text("清除")}
    }
    if(rows.isEmpty())CollectionEmpty("沒有符合的歷史記錄","一般瀏覽的網頁會出現在這裡。",Modifier.weight(1f))
    else LazyColumn(Modifier.weight(1f).testTag("history-list"),contentPadding=PaddingValues(horizontal=20.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        rows.groupBy{dayLabel(it.visitedAt)}.forEach{(day,items)->
            item(key="day:$day"){Text(day,Modifier.padding(top=8.dp,bottom=2.dp,start=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(items,key={it.url}){row->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){
                    Row(Modifier.fillMaxWidth().heightIn(min=78.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f).clickable{c.navigate(row.url)}.padding(start=16.dp,top=12.dp,bottom=12.dp)){
                            Text(row.title,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp));Text(Domains.scope(row.url),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick={c.store.removeHistory(row.url);c.revision++}){Icon(Icons.Outlined.Close,"移除這筆歷史",Modifier.size(18.dp))}
                    }
                }
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("清除歷史記錄？")},text={Text("會清除本機全部網頁歷史與搜尋詞，不刪除書籤、收藏或下載檔案。")},confirmButton={TextButton(onClick={c.store.clearHistory();c.revision++;confirm=false}){Text("清除歷史")}},dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
}

@Composable private fun ColumnScope.DownloadCollection(c:BrowserController) {
    val repository=remember(c){BrowserDownloads(c.context)}
    val scope=rememberCoroutineScope()
    var rows by remember{mutableStateOf<List<BrowserDownload>>(emptyList())}
    var error by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(true)}
    var refresh by remember{mutableIntStateOf(0)}
    var confirm by remember{mutableStateOf<BrowserDownload?>(null)}
    var search by remember{mutableStateOf("")}
    val activity=c.context as MainActivity
    LaunchedEffect(repository,refresh){
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(isActive){
                runCatching{withContext(Dispatchers.IO){repository.entries()}}.onSuccess{rows=it;error=""}.onFailure{error="暫時無法讀取系統下載，請重試"}
                loading=false
                delay(if(rows.any{it.active})1200 else 3500)
            }
        }
    }
    Text("只顯示由澄境瀏覽器下載的項目",Modifier.padding(horizontal=24.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),label={Text("搜尋下載檔案")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)})
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))
    if(error.isNotEmpty())Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(error,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("重試")}}
    val visible=rows.filter{it.title.contains(search,true)}
    if(!loading&&visible.isEmpty())CollectionEmpty("這裡還沒有下載項目","長按圖片或下載網頁檔案後，可在這裡查看進度與開啟檔案。",Modifier.weight(1f))
    else LazyColumn(Modifier.weight(1f).testTag("downloads-list"),contentPadding=PaddingValues(horizontal=20.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        items(visible,key={it.id}){row->
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.testTag("download:${row.id}")){
                Row(Modifier.fillMaxWidth().padding(start=16.dp,top=12.dp,bottom=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(if(row.complete)Icons.Outlined.InsertDriveFile else if(row.active)Icons.Outlined.Downloading else Icons.Outlined.ErrorOutline,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(enabled=row.complete){
                        scope.launch{runCatching{withContext(Dispatchers.IO){repository.openIntent(row.id)}}.onSuccess{intent->runCatching{c.context.startActivity(intent)}.onFailure{error="沒有可開啟此檔案的應用程式"}}.onFailure{error="檔案尚未完成、已被移除或無法開啟"}}
                    }){
                        Text(row.title,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        val size=if(row.total>0)Formatter.formatShortFileSize(c.context,row.total)else if(row.done>0)Formatter.formatShortFileSize(c.context,row.done)else "大小待確認"
                        Text("${row.label} · $size",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(row.active){Spacer(Modifier.height(8.dp));val progress=row.fraction;if(progress!=null)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())}
                    }
                    IconButton(onClick={confirm=row}){Icon(if(row.active)Icons.Outlined.Close else Icons.Outlined.DeleteOutline,if(row.active)"取消下載" else "刪除下載檔案",Modifier.size(20.dp))}
                }
            }
        }
    }
    confirm?.let{row->AlertDialog(onDismissRequest={confirm=null},title={Text(if(row.active)"取消這個下載？" else "刪除下載檔案？")},text={Text("${row.title}\n\n"+if(row.active)"停止下載並移除未完成的檔案。" else "將刪除檔案及系統下載項目，無法復原。")},confirmButton={TextButton(onClick={confirm=null;scope.launch{runCatching{withContext(Dispatchers.IO){repository.remove(row.id)}}.onFailure{error="此項目無法刪除或已不存在"};refresh++}}){Text(if(row.active)"取消下載" else "刪除")}},dismissButton={TextButton(onClick={confirm=null}){Text("保留")}})}
}

@Composable private fun CollectionEmpty(title:String,description:String,modifier:Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Text(title,style=MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp));Text(description,fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dayLabel(time:Long):String {
    if(time<=0)return "舊版保留的記錄"
    val today=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
    if(time>=today.timeInMillis)return "今天"
    today.add(Calendar.DAY_OF_YEAR,-1)
    return if(time>=today.timeInMillis)"昨天" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
}
