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
        title={Text(bt(R.string.msg_5f9f90a382dc))},
        text={
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(bt(R.string.msg_f2f22406c73c),style=MaterialTheme.typography.labelLarge)
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
                DataChoice(Icons.Outlined.History,bt(R.string.msg_0baa9a64e9b1),bt(R.string.msg_2a8cf901b0de ,pageCount,searchCount),history,!cleaner.running,"delete-history"){history=it}
                DataChoice(Icons.Outlined.Tab,bt(R.string.msg_8394223e0a97),bt(R.string.msg_18cfaeb0a53a ,tabCount),tabs,!cleaner.running,"delete-tabs"){tabs=it}
                HorizontalDivider()
                DataChoice(Icons.Outlined.Cookie,bt(R.string.msg_1f86b4081196),
                    if(cleaner.supportsSiteData)bt(R.string.msg_c44aefb98491)else bt(R.string.msg_dceb1d80c179),
                    siteData,!cleaner.running&&cleaner.supportsSiteData,"delete-site-data"){siteData=it}
                Text(bt(R.string.msg_ab54697044d0),style=MaterialTheme.typography.bodySmall)
                if(unknown&&range!=BrowsingTimeRange.ALL_TIME)Text(bt(R.string.msg_e3b65ee64f32),style=MaterialTheme.typography.bodySmall)
                Text(bt(R.string.msg_fec6ddf942c8),style=MaterialTheme.typography.bodySmall)
                if(cleaner.running)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("browsing-data-progress"))
                if(cleaner.error.isNotEmpty())Text(cleaner.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("browsing-data-error"))
            }
        },
        confirmButton={
            Button(onClick={cleaner.clear(range,BrowsingDataSelection(history,tabs,siteData))},enabled=!cleaner.running&&(history||tabs||siteData),modifier=Modifier.testTag("confirm-delete-browsing-data")){
                Text(if(cleaner.running)bt(R.string.msg_d2a1c725f6df)else bt(R.string.msg_6b0041cf4c37))
            }
        },
        dismissButton={TextButton(onClick={c.sheet=""},enabled=!cleaner.running){Text(bt(R.string.msg_2cd0f3be8738))}}
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
