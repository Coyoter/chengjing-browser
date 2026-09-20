package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable internal fun DeleteBrowsingDataDialog(c:BrowserController) {
    var range by remember{mutableStateOf(BrowsingTimeRange.LAST_15_MINUTES)}
    var expanded by remember{mutableStateOf(false)}
    var history by remember{mutableStateOf(true)}
    var tabs by remember{mutableStateOf(true)}
    // Finite-range choices must never silently expand deletion to all website data.
    var siteData by remember{mutableStateOf(false)}
    val cleaner=c.browsingDataCleaner
    val window=range.window(System.currentTimeMillis())
    c.revision
    val pages=c.store.historyPages()
    val searches=c.store.historySearches()
    val regular=c.tabs.filterNot{it.incognito}
    val pageCount=pages.count{window.contains(it.visitedAt)}
    val searchCount=searches.count{window.contains(it.searchedAt)}
    val tabCount=regular.count{window.contains(it.lastActiveAt)}
    val unknown=pages.any{it.visitedAt==0L}||searches.any{it.searchedAt==0L}||regular.any{it.lastActiveAt==0L}
    AlertDialog(
        onDismissRequest={if(!cleaner.running)c.sheet=""},
        modifier=Modifier.testTag("delete-browsing-data-dialog"),
        containerColor=MaterialTheme.colorScheme.surface,
        properties=DialogProperties(dismissOnBackPress=!cleaner.running,dismissOnClickOutside=!cleaner.running),
        title={Text("刪除瀏覽資料")},
        text={
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("時間範圍",style=MaterialTheme.typography.labelLarge)
                Box{
                    OutlinedButton(onClick={expanded=true},enabled=!cleaner.running,modifier=Modifier.fillMaxWidth().testTag("browsing-data-time-range")){
                        Text(range.label,Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)
                    }
                    DropdownMenu(expanded,onDismissRequest={expanded=false},containerColor=MaterialTheme.colorScheme.surface){
                        BrowsingTimeRange.entries.forEach{option->DropdownMenuItem(
                            text={Text(option.label)},
                            onClick={range=option;expanded=false},
                            modifier=Modifier.testTag("browsing-range-${option.name}")
                        )}
                    }
                }
                DataChoice(Icons.Outlined.History,"瀏覽記錄","$pageCount 個頁面、$searchCount 筆搜尋記錄",history,!cleaner.running,"delete-history"){history=it}
                DataChoice(Icons.Outlined.Tab,"尚未關閉的分頁","$tabCount 個一般分頁 · 依最後瀏覽時間關閉",tabs,!cleaner.running,"delete-tabs"){tabs=it}
                HorizontalDivider()
                DataChoice(Icons.Outlined.Cookie,"Cookie、快取和網站資料",
                    if(cleaner.supportsSiteData)"不限時間 · 全部清除，可能會登出網站"else"請先更新 Android System WebView 才能清除",
                    siteData,!cleaner.running&&cleaner.supportsSiteData,"delete-site-data"){siteData=it}
                Text("時間範圍適用於瀏覽記錄與分頁。Cookie、快取和網站資料只能全部清除。",style=MaterialTheme.typography.bodySmall)
                if(unknown&&range!=BrowsingTimeRange.ALL_TIME)Text("舊版未記錄時間的資料，請選「不限時間」刪除。",style=MaterialTheme.typography.bodySmall)
                Text("保留書籤、收藏、下載檔案與天眼設定。無痕資料請關閉無痕分頁清除。",style=MaterialTheme.typography.bodySmall)
                if(cleaner.running)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("browsing-data-progress"))
                if(cleaner.error.isNotEmpty())Text(cleaner.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("browsing-data-error"))
            }
        },
        confirmButton={
            Button(onClick={cleaner.clear(range,BrowsingDataSelection(history,tabs,siteData))},enabled=!cleaner.running&&(history||tabs||siteData),modifier=Modifier.testTag("confirm-delete-browsing-data")){
                Text(if(cleaner.running)"刪除中…"else"刪除資料")
            }
        },
        dismissButton={TextButton(onClick={c.sheet=""},enabled=!cleaner.running){Text("取消")}}
    )
}

@Composable private fun DataChoice(icon:ImageVector,title:String,description:String,checked:Boolean,enabled:Boolean,tag:String,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked,onCheckedChange=null,enabled=enabled)
    }
}
