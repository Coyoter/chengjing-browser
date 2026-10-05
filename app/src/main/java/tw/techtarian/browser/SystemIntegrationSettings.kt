package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable internal fun SystemIntegrationSettings(c:BrowserController){
    val activity=c.context as MainActivity
    var refresh by remember{mutableIntStateOf(0)}
    DisposableEffect(activity){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh++}
        activity.lifecycle.addObserver(observer)
        onDispose{activity.lifecycle.removeObserver(observer)}
    }
    val provider=remember(refresh){SystemAutofill.provider(activity)}
    val canInstall=remember(refresh){PackageDownloads.allowed(activity)}
    SettingsGroup(bt(R.string.system_password_title)){
        Text(provider?.let{bt(R.string.system_password_provider,it)}?:bt(R.string.system_password_none),color=MaterialTheme.colorScheme.primary)
        Text(bt(R.string.system_password_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={if(!SystemAutofill.openSettings(activity))c.notice=bt(R.string.system_settings_unavailable)},modifier=Modifier.fillMaxWidth().testTag("system-autofill-settings")){Text(bt(R.string.system_password_choose))}
        Text(bt(R.string.system_password_compatibility),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsGroup(bt(R.string.system_install_title)){
        Text(bt(if(canInstall)R.string.system_install_allowed else R.string.system_install_disabled),color=MaterialTheme.colorScheme.primary)
        Text(bt(R.string.system_install_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={runCatching{activity.managePackagePermission()}.onFailure{c.notice=bt(R.string.system_settings_unavailable)}},modifier=Modifier.fillMaxWidth().testTag("system-install-settings")){Text(bt(R.string.system_open_settings))}
    }
}
