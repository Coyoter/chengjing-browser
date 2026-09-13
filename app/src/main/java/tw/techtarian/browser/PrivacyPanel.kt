package tw.techtarian.browser

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp

@Composable internal fun PrivacyPanel(c:BrowserController){
    val policy=remember{c.context.assets.open("privacy.txt").bufferedReader().use{it.readText()}}
    policy.split("\n\n").forEach{part->
        Text(part,fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurface)
    }
}
