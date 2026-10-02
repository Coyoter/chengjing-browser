package tw.techtarian.browser

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable internal fun BrowserPanel(c:BrowserController,content:@Composable ColumnScope.()->Unit){
    val colors=MaterialTheme.colorScheme
    Dialog(onDismissRequest={c.sheet=""},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        val view=LocalView.current
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->
            if(c.privateScreen)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            browserSystemBars(window,view,colors.background.luminance()>.5f)
        }}
        Surface(Modifier.fillMaxSize().testTag("browser-panel"),color=colors.background){
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),content=content)
        }
    }
}

internal fun panelTitle(page:String)=when(page){
    "rule-editor"->bt(R.string.msg_35376f24c4ad);"rule-ai"->bt(R.string.msg_f3b8aca2eb95)
    "legal"->bt(R.string.msg_371d839ac65c);"element-editor"->bt(R.string.msg_eb2f0ca08efb);"develop-ai"->bt(R.string.msg_8a8f2d7c399a);"connection"->bt(R.string.msg_758380c7c9c7);"privacy"->bt(R.string.msg_930e319f7845);"menu"->bt(R.string.msg_bb11ab4df9b9);"settings"->bt(R.string.msg_0d8619aae051);"tabs"->bt(R.string.msg_842433f0a425);"eye"->bt(R.string.msg_320c5652f6d3);"selection"->bt(R.string.msg_559e4d37c3f8)
    "rules"->bt(R.string.msg_58f73b892e2d);"code"->bt(R.string.msg_e13bbc197118);"ai"->bt(R.string.msg_634ad289a228);"user-agent"->bt(R.string.msg_871eaa84c97d);"search-engine"->bt(R.string.msg_14bd2c678335)
    "inventory"->bt(R.string.msg_905d98bd5535);"sync"->bt(R.string.msg_4d94c28a53c4);"history"->bt(R.string.msg_0baa9a64e9b1);"downloads"->bt(R.string.msg_d477c75aa656);"domains"->bt(R.string.msg_f6b20e44de57)
    "find"->bt(R.string.msg_f7ff0dc054ff);"blocked"->bt(R.string.msg_9d08608859f8);else->bt(R.string.msg_bb11ab4df9b9)
}
internal fun panelParent(page:String)=when(page){
    "rule-editor"->"rules";"rule-ai"->"rule-editor"
    "menu","selection","connection"->"";"rules","code","ai","inventory"->"eye";"user-agent","search-engine","sync"->"settings";else->"menu"
}
@Composable internal fun PanelHeader(c:BrowserController,onBack:()->Unit){
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("panel-header").height(68.dp).padding(start=if(c.sheet=="menu")20.dp else 4.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
        if(c.sheet!="menu")IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,bt(R.string.msg_9357d631d10f))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(panelTitle(c.sheet),fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(c.sheet=="menu")Text(c.domain.ifBlank{bt(R.string.msg_59512d0db9fe)},fontSize=12.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(c.sheet=="tabs")TabOverviewMenu(c)
        else IconButton(onClick={c.sheet=""}){Icon(Icons.Outlined.Close,bt(R.string.msg_de961bb8936f))}
    }
}
@Composable internal fun MenuGroup(title:String,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(title,Modifier.padding(start=4.dp),fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface){Column(content=content)}
    }
}
@Composable internal fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit){
    MenuGroup(title){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)}
}
@Composable internal fun MenuRow(icon:ImageVector,title:String,subtitle:String="",navigation:Boolean=true,action:()->Unit){
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("menu-row:$title").clip(RoundedCornerShape(14.dp)).clickable(onClick=action).padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(34.dp).background(colors.primary.copy(alpha=.08f),CircleShape),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(20.dp),tint=colors.primary)}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,fontSize=15.sp,lineHeight=20.sp)
            if(subtitle.isNotEmpty())Text(subtitle,fontSize=12.sp,lineHeight=16.sp,color=colors.onSurfaceVariant)
        }
        if(navigation){Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(18.dp),tint=colors.onSurfaceVariant)}
    }
}
@Composable private fun Shortcut(icon:ImageVector,label:String,modifier:Modifier,action:()->Unit){
    Surface(modifier=modifier.height(76.dp).testTag("menu-shortcut:$label"),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,onClick=action){
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp,Alignment.CenterVertically)){
            Icon(icon,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}
@Composable internal fun BrowserMainMenu(c:BrowserController){
    val scope=rememberCoroutineScope();val tab=c.active;val store=c.store
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Shortcut(Icons.Outlined.Add,bt(R.string.msg_a38d62ae74d4),Modifier.weight(1f)){c.newTab(incognito=false);c.sheet=""}
        Shortcut(Icons.Outlined.StarOutline,bt(R.string.msg_60a53514eb92),Modifier.weight(1f)){c.sheet="favorites"}
        Shortcut(Icons.Outlined.BookmarkBorder,bt(R.string.msg_1eb1e5b30e73),Modifier.weight(1f)){c.sheet="bookmarks"}
        Shortcut(Icons.Outlined.History,bt(R.string.msg_0baa9a64e9b1),Modifier.weight(1f)){c.sheet="history"}
    }
    MenuGroup(bt(R.string.msg_97afce0d452c)){
        MenuRow(Icons.Outlined.PrivacyTip,bt(R.string.msg_ce7454d53ff8),bt(R.string.msg_97e7dbfaf405),navigation=false){if(c.newTab(incognito=true)!=null)c.sheet=""}
        MenuRow(Icons.Outlined.Download,bt(R.string.msg_d477c75aa656),bt(R.string.msg_8dce3f317048)){c.sheet="downloads"}
        MenuRow(Icons.Outlined.DeleteOutline,bt(R.string.msg_5f9f90a382dc),bt(R.string.msg_1b4a0f6f50a6)){c.sheet="clear-browsing-data"}
    }
    if(c.domain.isNotEmpty())MenuGroup(bt(R.string.msg_431596791167)){
        MenuRow(Icons.Outlined.Star,if(tab?.favoriteId!=null)bt(R.string.msg_20e1c9c1dc3d)else bt(R.string.msg_3e40d2036259),tab?.favoriteId?.let{c.favorites.get(it)?.title}.orEmpty(),navigation=false){scope.launch{if(c.saveFavorite()!=null){c.notice=bt(R.string.msg_b0e946efd4a1);c.sheet=""}}}
        MenuRow(Icons.Outlined.BookmarkAdd,bt(R.string.msg_5a8cabc73328),navigation=false){val count=store.bookmarkStore.add(tab!!.url,tab.title);c.revision++;c.notice=if(count>0)bt(R.string.msg_8fad34a6f7cd)else bt(R.string.msg_8aedb0dba45e);c.sheet=""}
        MenuRow(Icons.Outlined.Share,bt(R.string.msg_7e564575eb7d),bt(R.string.msg_f6eb7ace16a3),navigation=false){c.shareCurrentPage()}
        MenuRow(Icons.Outlined.Search,bt(R.string.msg_f7ff0dc054ff),navigation=false){c.findInPage.open()}
        MenuRow(Icons.Outlined.Computer,if(tab?.desktop==true)bt(R.string.msg_26cd04fd052a)else bt(R.string.msg_4fecc4be58d1),navigation=false){c.toggleDesktop();c.sheet=""}
    }
    MenuGroup(bt(R.string.msg_c74fe0d00aa6)){
        if(c.domain.isNotEmpty()){
            MenuRow(Icons.Outlined.Visibility,bt(R.string.msg_320c5652f6d3),bt(R.string.msg_3c8d8e95e622 ,c.site.rules.size)){c.sheet="eye"}
            MenuRow(Icons.Outlined.Shield,if(c.isException)bt(R.string.msg_5fa19ec01126)else bt(R.string.msg_3603b8ca2f4f),if(c.isException)bt(R.string.msg_4aaa7803adda)else bt(R.string.msg_43d22862b439),navigation=false){c.sheet="";c.exception()}
        }
        MenuRow(Icons.Outlined.Tune,bt(R.string.msg_d52aeefbc350)){c.sheet="domains"}
        if((tab?.blockedTotal?:0)>0)MenuRow(Icons.Outlined.Shield,bt(R.string.msg_1cd169ed0542 ,tab?.blockedTotal),bt(R.string.msg_0056af6aa02d)){tab?.blockedUnread=false;c.sheet="blocked"}
    }
    MenuGroup(bt(R.string.msg_6b0eef6c43e6)){
        MenuRow(Icons.Outlined.Settings,bt(R.string.msg_0d8619aae051),bt(R.string.msg_e7ed17309495)){c.sheet="settings"}
        MenuRow(Icons.Outlined.CloudSync,bt(R.string.msg_4d94c28a53c4)){c.sheet="sync"}
    }
    Text(bt(R.string.msg_28f9da8f03eb ,BuildConfig.VERSION_NAME),Modifier.fillMaxWidth().padding(start=4.dp),fontSize=11.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun SettingsCategories(category:String,onChange:(String)->Unit){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(Triple("appearance",bt(R.string.msg_b70682d027ce),Icons.Outlined.Palette),Triple("browsing",bt(R.string.msg_b4f5be24078a),Icons.Outlined.Language),Triple("ai","AI",Icons.Outlined.AutoAwesome)).forEach{(id,label,icon)->
            val selected=category==id
            Surface(modifier=Modifier.weight(1f).height(48.dp).semantics{this.selected=selected;role=Role.Tab},onClick={onChange(id)},shape=RoundedCornerShape(12.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){
                Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)){
                    Icon(icon,null,Modifier.size(18.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label,fontSize=14.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
@Composable internal fun AppearanceOption(label:String,selected:Boolean,modifier:Modifier=Modifier,icon:ImageVector?=null,addressBottom:Boolean?=null,onClick:()->Unit){
    val colors=MaterialTheme.colorScheme
    Surface(modifier=modifier.height(if(addressBottom==null)92.dp else 116.dp).semantics{this.selected=selected},onClick=onClick,shape=RoundedCornerShape(14.dp),color=if(selected)colors.primaryContainer else colors.background){
        Column(Modifier.fillMaxSize().padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)){
            if(addressBottom==null)Icon(icon!!,null,Modifier.size(25.dp),tint=if(selected)colors.primary else colors.onSurfaceVariant)
            else Column(Modifier.width(62.dp).height(57.dp).clip(RoundedCornerShape(7.dp)).background(colors.surface).padding(6.dp),verticalArrangement=Arrangement.SpaceBetween){
                val bar:@Composable ()->Unit={Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp)).background(colors.primary.copy(alpha=.65f)))}
                if(!addressBottom)bar()
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){repeat(2){Box(Modifier.fillMaxWidth(if(it==0)1f else .65f).height(3.dp).background(colors.onSurfaceVariant.copy(alpha=.2f)))}}
                if(addressBottom)bar()
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                if(selected)Icon(Icons.Outlined.Check,null,Modifier.size(13.dp),tint=colors.primary)
                Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
