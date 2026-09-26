package tw.techtarian.browser

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class NativePageFind(private val web:WebView):PageFindTarget {
    override fun listen(callback:((Int,Int,Boolean)->Unit)?){
        web.setFindListener(callback?.let{listener->WebView.FindListener{index,total,done->listener(index,total,done)}})
    }
    override fun find(query:String){web.findAllAsync(query)}
    override fun next(forward:Boolean){web.findNext(forward)}
    override fun clear(){web.clearMatches()}
}

internal fun BrowserController.openFindInPage(){
    val tab=active?:return
    if(tab.url.isBlank()||tab.imageContent!=null||tab.error.isNotEmpty()){
        notice="先開啟網頁，再尋找頁面文字";return
    }
    stopEye()
    pageFind.open(tab.id,NativePageFind(tab.web))
    sheet="find"
}
internal fun BrowserController.closeFindInPage(tabId:Int?=null){
    if(tabId!=null&&pageFind.ownerId!=tabId)return
    pageFind.close()
    if(sheet=="find")sheet=""
}

/** An inline strip, never a Dialog: the same live WebView remains mounted and visible. */
@Composable internal fun FindInPageBar(session:PageFindSession,onClose:()->Unit){
    val colors=MaterialTheme.colorScheme
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val requester=remember{FocusRequester()}
    fun move(forward:Boolean){
        focus.clearFocus(force=true);keyboard?.hide();session.move(forward)
    }
    LaunchedEffect(session.ownerId){
        withFrameNanos{}
        requester.requestFocus()
        keyboard?.show()
    }
    Surface(color=colors.surface,tonalElevation=1.dp,modifier=Modifier.fillMaxWidth().testTag("find-in-page-bar")){
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
            // Keep 48 dp controls and a useful input even with very large accessibility text.
            val stacked=maxWidth<300.dp||LocalDensity.current.fontScale>1.4f
            val field:@Composable (Modifier)->Unit={modifier->
                BasicTextField(value=session.query,onValueChange=session::update,singleLine=true,
                    textStyle=MaterialTheme.typography.bodyLarge.copy(color=colors.onSurface,fontSize=16.sp),
                    cursorBrush=SolidColor(colors.primary),
                    keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={move(true)}),
                    modifier=modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant.copy(alpha=.55f)).focusRequester(requester)
                        .testTag("find-in-page-input").semantics{contentDescription="尋找頁面文字"},
                    decorationBox={inner->
                        Box(Modifier.padding(horizontal=12.dp,vertical=12.dp),contentAlignment=Alignment.CenterStart){
                            if(session.query.isEmpty())Text("尋找頁面文字",fontSize=15.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            inner()
                        }
                    })
            }
            val actions:@Composable RowScope.()->Unit={
                Text(session.status,modifier=(if(stacked)Modifier.weight(1f)else Modifier.width(48.dp))
                    .padding(horizontal=4.dp).testTag("find-in-page-count")
                    .semantics{contentDescription=session.description;liveRegion=LiveRegionMode.Polite},
                    color=if(session.failed||(session.query.isNotEmpty()&&!session.searching&&session.count==0))colors.error else colors.onSurfaceVariant,
                    style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={move(false)},enabled=session.canMove,modifier=Modifier.size(48.dp).testTag("find-in-page-previous")){
                    Icon(Icons.Outlined.KeyboardArrowUp,"上一筆符合文字",Modifier.size(22.dp))
                }
                IconButton(onClick={move(true)},enabled=session.canMove,modifier=Modifier.size(48.dp).testTag("find-in-page-next")){
                    Icon(Icons.Outlined.KeyboardArrowDown,"下一筆符合文字",Modifier.size(22.dp))
                }
                IconButton(onClick={focus.clearFocus(force=true);keyboard?.hide();onClose()},modifier=Modifier.size(48.dp).testTag("find-in-page-close")){
                    Icon(Icons.Outlined.Close,"關閉頁面搜尋",Modifier.size(22.dp))
                }
            }
            if(stacked)Column{field(Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,content=actions)}
            else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(0.dp)){
                field(Modifier.weight(1f));actions()
            }
        }
    }
}
