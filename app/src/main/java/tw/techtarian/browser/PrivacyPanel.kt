package tw.techtarian.browser

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp

@Composable internal fun PrivacyPanel(c:BrowserController){
    val policy=remember(AppLanguages.currentTag){c.context.assets.open("i18n/${AppLanguages.currentTag}/privacy.txt").bufferedReader(Charsets.UTF_8).use{it.readText()}}
    TextButton(onClick={c.navigate("https://techtarian.com/chengjing-browser/privacy/")}){Text(bt(R.string.msg_a326ad3c84c9))}
    policy.split("\n\n").forEach{part->
        Text(part,fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable internal fun LicensePanel(c:BrowserController){
    val notices=remember{c.context.assets.open("third-party-notices.txt").bufferedReader().use{it.readText()}}
    notices.split("\n\n").forEach{Text(it,fontSize=12.sp,lineHeight=19.sp)}
}
