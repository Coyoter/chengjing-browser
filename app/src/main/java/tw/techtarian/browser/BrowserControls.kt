package tw.techtarian.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Preserve the v1.2.0 first-level toolbar. Sharing belongs only in the overflow menu. */
@Composable internal fun BrowserControls(c:BrowserController,onAction:()->Unit) {
    val cs=MaterialTheme.colorScheme
    val active=c.active
    c.revision
    val pageFavorite=c.favorites.forPage(active?.url.orEmpty())
    val scope=rememberCoroutineScope()
    Surface(color=cs.surface){
        Row(Modifier.fillMaxWidth().testTag("browser-controls").height(64.dp).padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Tool(Icons.AutoMirrored.Outlined.ArrowBack,"上一頁",active?.canBack==true){c.stopEye();active?.web?.goBack()}
            Tool(Icons.AutoMirrored.Outlined.ArrowForward,"下一頁",active?.canForward==true){c.stopEye();active?.web?.goForward()}
            FilledTonalButton(onClick={onAction();c.beginEye()},contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){
                Icon(Icons.Outlined.Visibility,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("天眼",fontWeight=FontWeight.SemiBold)
                if(c.site.rules.isNotEmpty()){Spacer(Modifier.width(6.dp));Text("${c.site.rules.size}",fontSize=12.sp)}
            }
            Tool(if(pageFavorite!=null)Icons.Filled.Star else Icons.Outlined.StarOutline,if(active?.favoriteId!=null||pageFavorite!=null)"更新收藏進度"else"快速收藏",c.domain.isNotEmpty(),modifier=Modifier.testTag("quick-favorite").semantics{stateDescription=if(pageFavorite!=null)"已收藏"else"尚未收藏"}){
                onAction()
                scope.launch{if(c.saveFavorite()!=null)c.notice="已保存收藏與閱讀位置"}
            }
            Box(Modifier.size(48.dp)){
                Tool(Icons.Outlined.MoreHoriz,"瀏覽器選單"){onAction();active?.blockedUnread=false;c.sheet="menu"}
                if(active?.blockedUnread==true)Box(Modifier.size(5.dp).testTag("blocking-indicator").align(Alignment.TopEnd).offset(x=(-8).dp,y=8.dp).background(cs.primary,CircleShape))
            }
        }
    }
}
@Composable private fun Tool(icon:ImageVector,label:String,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit){
    IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp)){Icon(icon,label,Modifier.size(22.dp))}
}
