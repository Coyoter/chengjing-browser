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
    SettingsGroup(bt(R.string.msg_9c9624b64441)){
        Text(location.status,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.testTag("location-status"))
        if(!location.nativeAttached(tab.id))Text(bt(R.string.msg_88c8eedbe2ec),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(tab.incognito)bt(R.string.msg_68f8f532449b)else bt(R.string.msg_db3dea807cf5),fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LocationChoice.entries.forEach{choice->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(location.choice(tab)==choice,role=Role.RadioButton){location.setChoice(tab,choice)}
                .testTag("location-choice:${choice.name}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                RadioButton(location.choice(tab)==choice,null)
                Text(choice.label,style=MaterialTheme.typography.bodyLarge)
            }
        }
        Text(bt(R.string.msg_5eb5c24ef2b0),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={location.openAppSettings()}){Text(bt(R.string.msg_d47077aa2c28))}
        TextButton(onClick={location.openLocationSettings()}){Text(bt(R.string.msg_357f6ad5cd3b))}
    }
}
