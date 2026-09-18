package tw.techtarian.browser

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BrowserHome(c:BrowserController,store:BrowserStore){
    var intro by remember(c.active?.id){mutableStateOf<Boolean?>(null)}
    LaunchedEffect(c.active?.id){intro=c.home.claimIntroduction()}
    val day=rememberHomeDate()
    val quote=remember(day,intro){
        if(intro==false)c.homeQuotes[HomePolicy.quoteIndex(c.home.quoteStart(day),day,c.homeQuotes.size)]else ""
    }
    val cs=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().testTag("browser-home").verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=38.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.ic_launcher),"澄境瀏覽器",modifier=Modifier.size(56.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("澄境瀏覽器",fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text("CHENGJING BROWSER",fontSize=9.sp,letterSpacing=1.6.sp,color=cs.onSurfaceVariant)}}
        Spacer(Modifier.height(40.dp))
        if(intro==true){
            Column(Modifier.testTag("home-introduction")){
                DailyQuoteText("網頁，\n依你的習慣調整。")
                Spacer(Modifier.height(18.dp))
                Text("照常瀏覽，也能打開天眼，\n新增樣式、程式碼或調整元件。",fontSize=15.sp,lineHeight=25.sp,color=cs.onSurfaceVariant)
            }
        }else if(intro==false){
            Column(Modifier.fillMaxWidth().heightIn(min=196.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                Text("今日一句",fontSize=11.sp,letterSpacing=2.sp,color=cs.primary)
                DailyQuoteText(quote)
            }
        }else Spacer(Modifier.height(196.dp))
        Spacer(Modifier.height(28.dp))
        Text("快速前往",fontSize=12.sp,color=cs.onSurfaceVariant);Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            AssistChip(onClick={c.navigate("https://www.google.com")},label={Text("Google")},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(16.dp))})
            AssistChip(onClick={c.navigate("https://zh.wikipedia.org")},label={Text("維基百科")},leadingIcon={Icon(Icons.Outlined.Language,null,Modifier.size(16.dp))})
        }
        val favoriteRevision=c.revision
        val recentFavorites=remember(favoriteRevision){c.favorites.all().take(3)}
        if(recentFavorites.isNotEmpty()){
            Spacer(Modifier.height(18.dp));Text("繼續閱讀",fontSize=12.sp,color=cs.onSurfaceVariant)
            recentFavorites.forEach{favorite->Row(Modifier.fillMaxWidth().clickable{c.openFavorite(favorite)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){SiteIcon(c,favorite.url,28.dp);Spacer(Modifier.width(10.dp));Text(favorite.title,Modifier.weight(1f),fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(onClick={c.sheet="bookmarks"}){Icon(Icons.Outlined.Folder,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("書籤資料夾")}
            TextButton(onClick={c.sheet="favorites"}){Icon(Icons.Outlined.StarOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("所有收藏")}
        }
    }
}

/** Width-led sizing with unconstrained height: never ellipsize the user's sentence. */
@Composable internal fun DailyQuoteText(text:String){
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val pixels=with(density){maxWidth.roundToPx().coerceAtLeast(1)}
        val style=remember(text,pixels,density.fontScale,measurer){
            (34 downTo 22).map{size->TextStyle(fontSize=size.sp,lineHeight=(size*1.42f).sp,fontWeight=FontWeight.Light)}
                .firstOrNull{candidate->measurer.measure(AnnotatedString(text),candidate,constraints=Constraints(maxWidth=pixels)).lineCount<=4}
                ?:TextStyle(fontSize=22.sp,lineHeight=32.sp,fontWeight=FontWeight.Light)
        }
        // No maxLines/fixed height: large accessibility fonts may make the page scroll.
        Text(text,Modifier.fillMaxWidth().testTag("daily-home-quote"),style=style,color=MaterialTheme.colorScheme.onBackground)
    }
}

@Composable private fun rememberHomeDate():LocalDate {
    var day by remember{mutableStateOf(LocalDate.now())}
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context,lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)day=LocalDate.now()}
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){day=LocalDate.now()}}
        val filter=IntentFilter().apply{addAction(Intent.ACTION_DATE_CHANGED);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED)}
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose{lifecycle.removeObserver(observer);context.unregisterReceiver(receiver)}
    }
    LaunchedEffect(lifecycle){
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){day=LocalDate.now();delay(30_000)}
        }
    }
    return day
}
