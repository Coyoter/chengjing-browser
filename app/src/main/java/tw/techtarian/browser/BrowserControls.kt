package tw.techtarian.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

/** Six equal touch areas fit 320 dp phones; very narrow split windows can scroll. */
@Composable internal fun BrowserControls(c:BrowserController,onAction:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val tab=c.active
    c.revision
    val favorite=c.favorites.forPage(tab?.url.orEmpty())
    val scope=rememberCoroutineScope()
    Surface(color=colors.surface){
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("browser-controls").height(64.dp)){
            val barWidth=maxOf(maxWidth,304.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()).width(barWidth).fillMaxHeight().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                Control(Icons.AutoMirrored.Outlined.ArrowBack,"上一頁",Modifier.weight(1f),tab?.canBack==true){onAction();c.stopEye();tab?.web?.goBack()}
                Control(Icons.AutoMirrored.Outlined.ArrowForward,"下一頁",Modifier.weight(1f),tab?.canForward==true){onAction();c.stopEye();tab?.web?.goForward()}
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center){
                    FilledTonalButton(onClick={onAction();c.beginEye()},modifier=Modifier.size(48.dp),contentPadding=PaddingValues(0.dp)){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Icon(Icons.Outlined.Visibility,null,Modifier.size(18.dp))
                            Text("天眼",fontSize=10.sp,lineHeight=13.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                    if(c.site.rules.isNotEmpty())Badge(Modifier.align(Alignment.TopEnd).offset(y=(-2).dp)){Text("${c.site.rules.size}")}
                }
                Control(if(favorite!=null)Icons.Filled.Star else Icons.Outlined.StarOutline,if(tab?.favoriteId!=null||favorite!=null)"更新收藏進度"else"快速收藏",Modifier.weight(1f).testTag("quick-favorite").semantics{stateDescription=if(favorite!=null)"已收藏"else"尚未收藏"},c.domain.isNotEmpty()){
                    onAction();scope.launch{if(c.saveFavorite()!=null)c.notice="已保存收藏與閱讀位置"}
                }
                Control(Icons.Outlined.Share,"分享目前網頁",Modifier.weight(1f).testTag("share-current-page"),PageActionPolicy.shareUrl(tab?.url)!=null){onAction();c.shareCurrentPage()}
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center){
                    Control(Icons.Outlined.MoreHoriz,"瀏覽器選單",Modifier.fillMaxWidth()) {onAction();tab?.blockedUnread=false;c.sheet="menu"}
                    if(tab?.blockedUnread==true)Box(Modifier.size(5.dp).testTag("blocking-indicator").align(Alignment.TopEnd).offset(x=(-8).dp,y=8.dp).background(colors.primary,CircleShape))
                }
            }
        }
    }
}
@Composable private fun Control(icon:ImageVector,label:String,modifier:Modifier,enabled:Boolean=true,onClick:()->Unit){
    IconButton(onClick=onClick,enabled=enabled,modifier=modifier.height(48.dp)){Icon(icon,label,Modifier.size(22.dp))}
}
