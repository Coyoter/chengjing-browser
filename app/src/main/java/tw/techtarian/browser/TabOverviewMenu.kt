package tw.techtarian.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Only the tab-overview header owns this menu; it never changes the browser toolbar. */
@Composable internal fun TabOverviewMenu(c:BrowserController) {
    val colors=MaterialTheme.colorScheme
    val privateGroup=c.overviewPrivate
    // A dismissed/reopened overview or a different collection must not reuse an open menu.
    var expanded by remember(privateGroup){mutableStateOf(false)}
    var closingGroup by remember{mutableStateOf<Boolean?>(null)}
    Box {
        IconButton(onClick={expanded=!expanded},modifier=Modifier.size(48.dp).testTag("tab-overview-menu")) {
            Icon(Icons.Outlined.MoreVert,"分頁選單",Modifier.size(24.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.widthIn(min=188.dp),
            shape=RoundedCornerShape(14.dp),containerColor=colors.surface,tonalElevation=0.dp) {
            DropdownMenuItem(
                text={Text("關閉所有分頁",fontSize=15.sp)},
                enabled=c.tabs.any{it.incognito==privateGroup},
                onClick={expanded=false;closingGroup=privateGroup},
                modifier=Modifier.testTag("close-all-tabs")
            )
        }
    }
    closingGroup?.let{group->
        val count=c.tabs.count{it.incognito==group}
        AlertDialog(
            onDismissRequest={closingGroup=null},
            title={Text(if(group)"關閉所有無痕分頁？"else"關閉所有一般分頁？")},
            text={Text("將關閉這一組的 $count 個分頁，不影響另一組。"+
                if(group)"無痕網站資料會在全部關閉後清理。下載檔案、書籤與收藏仍會保留。"
                else "這些分頁與快照不會在下次啟動時還原；歷史記錄、下載檔案、書籤與收藏仍會保留。")},
            confirmButton={TextButton(enabled=count>0,onClick={
                val onlyGroup=c.tabs.all{it.incognito==group}
                c.closeAllTabs(group)
                closingGroup=null
                // After closing the whole session, show its fresh homepage instead of an
                // apparently unclosed blank card. A surviving other group stays untouched.
                if(onlyGroup)c.sheet=""
            },modifier=Modifier.testTag("confirm-close-all-tabs")){Text("全部關閉")}},
            dismissButton={TextButton(onClick={closingGroup=null},modifier=Modifier.testTag("cancel-close-all-tabs")){Text("取消")}}
        )
    }
}
