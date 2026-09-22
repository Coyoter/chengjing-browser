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
    "legal"->"第三方授權";"element-editor"->"元件程式碼";"develop-ai"->"天眼 AI";"connection"->"網站連線";"privacy"->"隱私與資料";"menu"->"澄境瀏覽器";"settings"->"設定";"tabs"->"分頁";"eye"->"天眼設定";"selection"->"選中一個元件"
    "rules"->"網站修改";"code"->"自訂程式碼";"ai"->"AI 協助";"user-agent"->"瀏覽器識別"
    "inventory"->"結構清單";"sync"->"Google 同步";"history"->"瀏覽記錄";"downloads"->"下載";"domains"->"網域規則"
    "find"->"尋找頁面文字";"blocked"->"攔截紀錄";else->"澄境瀏覽器"
}
internal fun panelParent(page:String)=when(page){
    "menu","selection","connection"->"";"rules","code","ai","inventory"->"eye";"user-agent","sync"->"settings";else->"menu"
}
@Composable internal fun PanelHeader(c:BrowserController,onBack:()->Unit){
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("panel-header").height(68.dp).padding(start=if(c.sheet=="menu")20.dp else 4.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
        if(c.sheet!="menu")IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回上一層")}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(panelTitle(c.sheet),fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(c.sheet=="menu")Text(c.domain.ifBlank{"依你的習慣瀏覽"},fontSize=12.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(c.sheet=="tabs")TabOverviewMenu(c)
        else IconButton(onClick={c.sheet=""}){Icon(Icons.Outlined.Close,"關閉選單")}
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
    Row(Modifier.fillMaxWidth().height(64.dp).testTag("menu-row:$title").clip(RoundedCornerShape(14.dp)).clickable(onClick=action).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(34.dp).background(colors.primary.copy(alpha=.08f),CircleShape),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(20.dp),tint=colors.primary)}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,fontSize=15.sp,lineHeight=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(subtitle.isNotEmpty())Text(subtitle,fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
        }
        if(navigation){Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(18.dp),tint=colors.onSurfaceVariant)}
    }
}
@Composable private fun Shortcut(icon:ImageVector,label:String,modifier:Modifier,action:()->Unit){
    Surface(modifier=modifier.height(76.dp).testTag("menu-shortcut:$label"),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,onClick=action){
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp,Alignment.CenterVertically)){
            Icon(icon,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
@Composable internal fun BrowserMainMenu(c:BrowserController){
    val scope=rememberCoroutineScope();val tab=c.active;val store=c.store
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Shortcut(Icons.Outlined.Add,"新增分頁",Modifier.weight(1f)){c.newTab(incognito=false);c.sheet=""}
        Shortcut(Icons.Outlined.StarOutline,"收藏",Modifier.weight(1f)){c.sheet="favorites"}
        Shortcut(Icons.Outlined.BookmarkBorder,"書籤",Modifier.weight(1f)){c.sheet="bookmarks"}
        Shortcut(Icons.Outlined.History,"瀏覽記錄",Modifier.weight(1f)){c.sheet="history"}
    }
    MenuGroup("瀏覽工具"){
        MenuRow(Icons.Outlined.PrivacyTip,"新增無痕分頁","與一般分頁分開的瀏覽工作階段",navigation=false){if(c.newTab(incognito=true)!=null)c.sheet=""}
        MenuRow(Icons.Outlined.Download,"下載","檢視澄境瀏覽器下載的項目"){c.sheet="downloads"}
        MenuRow(Icons.Outlined.DeleteOutline,"刪除瀏覽資料","選擇時間範圍與要刪除的項目"){c.sheet="clear-browsing-data"}
    }
    if(c.domain.isNotEmpty())MenuGroup("目前頁面"){
        MenuRow(Icons.Outlined.Star,if(tab?.favoriteId!=null)"更新收藏進度"else"收藏目前頁面",tab?.favoriteId?.let{c.favorites.get(it)?.title}.orEmpty(),navigation=false){scope.launch{if(c.saveFavorite()!=null){c.notice="已保存收藏與閱讀位置";c.sheet=""}}}
        MenuRow(Icons.Outlined.BookmarkAdd,"加入書籤",navigation=false){val count=store.bookmarkStore.add(tab!!.url,tab.title);c.revision++;c.notice=if(count>0)"已加入書籤的未分類資料夾"else"這個頁面已在書籤裡";c.sheet=""}
        MenuRow(Icons.Outlined.Share,"分享","使用 Android 系統分享",navigation=false){c.shareCurrentPage()}
        MenuRow(Icons.Outlined.Search,"尋找頁面文字"){c.sheet="find"}
        MenuRow(Icons.Outlined.Computer,if(tab?.desktop==true)"切換手機版網站"else"切換電腦版網站",navigation=false){c.toggleDesktop();c.sheet=""}
    }
    MenuGroup("天眼與網站"){
        if(c.domain.isNotEmpty()){
            MenuRow(Icons.Outlined.Visibility,"天眼設定","${c.site.rules.size} 條移除規則"){c.sheet="eye"}
            MenuRow(Icons.Outlined.Shield,if(c.isException)"恢復套用天眼規則"else"暫時顯示原始網站",if(c.isException)"目前顯示原始網站"else"暫停此網域的設定",navigation=false){c.sheet="";c.exception()}
        }
        MenuRow(Icons.Outlined.Tune,"所有網域規則"){c.sheet="domains"}
        if((tab?.blockedTotal?:0)>0)MenuRow(Icons.Outlined.Shield,"已攔截 ${tab?.blockedTotal} 次干擾","查看本分頁紀錄"){tab?.blockedUnread=false;c.sheet="blocked"}
    }
    MenuGroup("偏好與資料"){
        MenuRow(Icons.Outlined.Settings,"設定","外觀與 AI 設定"){c.sheet="settings"}
        MenuRow(Icons.Outlined.CloudSync,"Google 同步"){c.sheet="sync"}
    }
    Text("澄境瀏覽器 ${BuildConfig.VERSION_NAME}",Modifier.fillMaxWidth().padding(start=4.dp),fontSize=11.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun SettingsCategories(category:String,onChange:(String)->Unit){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(Triple("appearance","外觀",Icons.Outlined.Palette),Triple("browsing","瀏覽",Icons.Outlined.Language),Triple("ai","AI",Icons.Outlined.AutoAwesome)).forEach{(id,label,icon)->
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
                Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
