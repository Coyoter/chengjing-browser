package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable internal fun WebsitePermissionsPanel(c:BrowserController){
    val tab=c.active?:return
    if(LocationOrigin.of(tab.url)==null)return
    val activity=c.context as MainActivity
    val permissions=activity.websitePermissions
    c.revision
    SettingsGroup(bt(R.string.web_permissions)){
        Text(bt(R.string.web_permission_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        WebsiteResource.entries.forEach{resource->
            var expanded by remember(tab.id,resource){mutableStateOf(false)}
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                Text(resource.label,Modifier.weight(1f))
                Box{
                    TextButton(onClick={expanded=true},modifier=Modifier.testTag("website-permission:${resource.name}")){
                        Text(permissions.choice(tab,resource).label)
                    }
                    DropdownMenu(expanded,{expanded=false}){
                        LocationChoice.entries.forEach{choice->
                            DropdownMenuItem(text={Text(choice.label)},onClick={expanded=false;permissions.setChoice(tab,resource,choice)},modifier=Modifier.testTag("website-permission:${resource.name}:${choice.name}"))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(bt(R.string.web_third_party_cookies),Modifier.weight(1f))
            Switch(permissions.thirdPartyCookies(tab),{permissions.setThirdPartyCookies(tab,it)},Modifier.testTag("website-third-party-cookies"))
        }
        Text(bt(R.string.web_cookie_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={activity.websiteLocation.openAppSettings()}){Text(bt(R.string.web_app_permissions))}
    }
}
