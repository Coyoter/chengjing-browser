package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun LocationPanel(c:BrowserController){
    val tab=c.active?:return
    val location=(c.context as MainActivity).websiteLocation
    val origin=LocationOrigin.of(tab.url)?:return
    c.revision
    SettingsGroup("網站定位"){
        Text(if(tab.incognito)"無痕授權只保留到關閉全部無痕分頁。"else"只套用此網站；其他網站需另行取得授權。",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LocationChoice.entries.forEach{choice->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(location.choice(tab)==choice,role=Role.RadioButton){location.setChoice(tab,choice)}
                .testTag("location-choice:${choice.name}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                RadioButton(location.choice(tab)==choice,null)
                Text(choice.label,style=MaterialTheme.typography.bodyLarge)
            }
        }
        Text("變更後會重新載入這個網站，立即停止先前的定位請求。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={location.openAppSettings()}){Text("手機定位權限")}
        TextButton(onClick={location.openLocationSettings()}){Text("手機定位服務設定")}
    }
}
