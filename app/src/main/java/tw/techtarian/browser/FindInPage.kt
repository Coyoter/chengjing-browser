package tw.techtarian.browser

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
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

/** One transient native search, owned by the currently displayed WebView. No JS or persistence. */
internal class FindInPage(private val controller:BrowserController) {
    private val handler=Handler(Looper.getMainLooper())
    private var owner:BrowserTab?=null
    private var request=0L
    private var pending:Runnable?=null
    var isOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var matches by mutableIntStateOf(0)
        private set
    var current by mutableIntStateOf(0)
        private set
    var searching by mutableStateOf(false)
        private set
    val canMove get()=isOpen&&!searching&&matches>0
    val resultLabel get()=when {
        query.isEmpty()->"0 / 0"
        searching->"搜尋中"
        matches==0->"找不到"
        else->"$current / $matches"
    }
    val resultDescription get()=when {
        query.isEmpty()->"請輸入要尋找的文字"
        searching->"正在搜尋頁面"
        matches==0->"找不到符合的文字"
        else->"第 $current 筆，共 $matches 筆"
    }

    fun open():Boolean {
        val tab=controller.active?:return false
        if(tab.url.isEmpty()||tab.error.isNotEmpty()||tab.imageContent!=null){
            controller.notice="先開啟網頁，再尋找頁面文字";return false
        }
        // Do not discard unsaved Sky Eye edits just to begin a search.
        if(controller.eye||tab.web.selecting){controller.notice="請先離開天眼，再尋找頁面文字";return false}
        close()
        controller.sheet=""
        owner=tab
        isOpen=true
        return true
    }
    private fun owns(tab:BrowserTab)=isOpen&&owner===tab&&controller.active===tab&&tab in controller.tabs

    fun update(value:String){
        val tab=owner?:return
        if(!owns(tab)||value==query)return
        query=value
        search(tab,debounce=true)
    }
    private fun search(tab:BrowserTab,debounce:Boolean){
        pending?.let{handler.removeCallbacks(it)};pending=null
        val generation=++request
        val text=query
        matches=0;current=0;searching=text.isNotEmpty()
        // Successive findAllAsync calls cancel native searches; detach the old listener
        // during debounce so an old count is never displayed for freshly typed text.
        tab.web.setFindListener(null)
        tab.web.findAllAsync("")
        tab.web.clearMatches()
        if(text.isEmpty())return
        val task=Runnable{
            if(!owns(tab)||request!=generation||query!=text)return@Runnable
            pending=null
            tab.web.setFindListener{ordinal,count,done->
                if(owns(tab)&&request==generation&&query==text){
                    matches=count.coerceAtLeast(0)
                    current=if(matches==0)0 else (ordinal+1).coerceIn(1,matches)
                    searching=!done
                }
            }
            tab.web.findAllAsync(text)
        }
        if(debounce){pending=task;handler.postDelayed(task,120)}else task.run()
    }
    fun move(forward:Boolean){
        val tab=owner?:return
        if(owns(tab)&&canMove)tab.web.findNext(forward)
    }
    fun submit(){
        val tab=owner?:return
        if(!owns(tab))return
        if(pending!=null)search(tab,debounce=false)else move(true)
    }
    fun pageFinished(tab:BrowserTab){
        // A user may start typing before the initial document finishes loading.
        if(owns(tab)&&query.isNotEmpty())search(tab,debounce=false)
    }
    fun closeFor(tabId:Int){if(owner?.id==tabId)close()}
    fun close(){
        val tab=owner
        request++
        pending?.let{handler.removeCallbacks(it)};pending=null
        owner=null;isOpen=false;query="";matches=0;current=0;searching=false
        // All call sites close before WebView.destroy; this extra guard also protects
        // late disposal after an Activity or tab has already been removed.
        if(tab!=null&&tab in controller.tabs)runCatching{
            tab.web.setFindListener(null);tab.web.findAllAsync("");tab.web.clearMatches()
        }
    }
}

/** In-layout, not a Dialog: the same WebView remains attached and scrollable below it. */
@Composable internal fun FindInPageBar(find:FindInPage,requestKeyboard:Boolean=true){
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val requester=remember{FocusRequester()}
    val colors=MaterialTheme.colorScheme
    fun finish(){focus.clearFocus(force=true);keyboard?.hide();find.close()}
    fun next(forward:Boolean){focus.clearFocus(force=true);keyboard?.hide();find.move(forward)}
    LaunchedEffect(find){
        if(requestKeyboard){
            // Wait until the old full-screen menu has been dismissed and the bar laid out.
            withFrameNanos{}
            focus.clearFocus(force=true);requester.requestFocus();keyboard?.show()
        }
    }
    DisposableEffect(find){onDispose{focus.clearFocus(force=true);keyboard?.hide()}}
    Surface(color=colors.surface,modifier=Modifier.fillMaxWidth().testTag("find-in-page-bar")){
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
            val stacked=maxWidth<344.dp||LocalDensity.current.fontScale>1.3f
            val input:@Composable (Modifier)->Unit={modifier->
                OutlinedTextField(value=find.query,onValueChange=find::update,
                    modifier=modifier.testTag("find-query").focusRequester(requester).onPreviewKeyEvent{event->
                        when(event.key){
                            Key.Escape->{if(event.type==KeyEventType.KeyDown)finish();true}
                            Key.Enter,Key.NumPadEnter->{
                                if(event.type==KeyEventType.KeyDown){
                                    if(event.isShiftPressed)next(false)else{find.submit();focus.clearFocus();keyboard?.hide()}
                                }
                                true
                            }
                            else->false
                        }
                    },
                    placeholder={Text("尋找頁面文字",maxLines=1,overflow=TextOverflow.Ellipsis)},
                    singleLine=true,shape=RoundedCornerShape(12.dp),
                    keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={find.submit();focus.clearFocus();keyboard?.hide()}))
            }
            val result:@Composable (Modifier)->Unit={modifier->
                Text(find.resultLabel,modifier.testTag("find-result").semantics{
                    contentDescription=find.resultDescription;liveRegion=LiveRegionMode.Polite
                },fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                    color=if(find.query.isNotEmpty()&&!find.searching&&find.matches==0)colors.error else colors.onSurfaceVariant)
            }
            val previous:@Composable ()->Unit={
                IconButton(onClick={next(false)},enabled=find.canMove,modifier=Modifier.size(48.dp).testTag("find-previous")){
                    Icon(Icons.Outlined.KeyboardArrowUp,"上一筆符合文字")
                }
            }
            val following:@Composable ()->Unit={
                IconButton(onClick={next(true)},enabled=find.canMove,modifier=Modifier.size(48.dp).testTag("find-next")){
                    Icon(Icons.Outlined.KeyboardArrowDown,"下一筆符合文字")
                }
            }
            val close:@Composable ()->Unit={
                IconButton(onClick={finish()},modifier=Modifier.size(48.dp).testTag("find-close")){
                    Icon(Icons.Outlined.Close,"關閉頁面搜尋")
                }
            }
            if(stacked)Column{
                Row(verticalAlignment=Alignment.CenterVertically){input(Modifier.weight(1f));close()}
                Row(verticalAlignment=Alignment.CenterVertically){result(Modifier.weight(1f).padding(start=12.dp));previous();following()}
            }else Row(verticalAlignment=Alignment.CenterVertically){
                input(Modifier.weight(1f));result(Modifier.padding(horizontal=8.dp).widthIn(max=88.dp));previous();following();close()
            }
        }
    }
}
