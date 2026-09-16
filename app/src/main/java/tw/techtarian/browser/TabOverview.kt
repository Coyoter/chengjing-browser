package tw.techtarian.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun TabOverview(c:BrowserController){
    var query by remember{mutableStateOf("")}
    var closePrivate by remember{mutableStateOf(false)}
    val private=c.tabCollection
    val rows=c.tabs.filter{it.incognito==private&&(it.title.contains(query,true)||it.url.contains(query,true))}
    val colors=MaterialTheme.colorScheme
    BrowserPanel(c){
        PanelHeader(c){c.sheet=""}
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                listOf(false,true).forEach{mode->
                    FilterChip(selected=private==mode,onClick={c.tabCollection=mode;query=""},modifier=Modifier.weight(1f).testTag(if(mode)"tabs-private"else"tabs-normal").semantics{selected=private==mode;role=Role.Tab},label={Text("${if(mode)"無痕"else"一般"}  ${c.tabs.count{it.incognito==mode}}")},leadingIcon={Icon(if(mode)Icons.Outlined.VisibilityOff else Icons.Outlined.Tab,null,Modifier.size(18.dp))})
                }
            }
            RecordSearch(query,{query=it},"搜尋開啟的分頁","tab-search")
            if(private){
                Text("無痕分頁不保存瀏覽與搜尋紀錄，關閉全部後清除這次的網站資料。",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                if(!c.privateSession.supported)Text("目前 WebView 不支援隔離的無痕環境，請先更新 Android System WebView。",style=MaterialTheme.typography.bodySmall,color=colors.error)
            }
            if(rows.isEmpty())RecordEmpty(if(query.isNotBlank())"找不到符合的分頁"else if(private)"留一個不被記錄的空間"else"從一個新分頁開始",if(private)"不與一般分頁共用網站登入狀態。"else"開啟的網頁會整理在這裡。")
            LazyVerticalGrid(columns=GridCells.Adaptive(152.dp),modifier=Modifier.weight(1f).fillMaxWidth().testTag("tab-grid"),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=8.dp)){
                items(rows,key={it.id}){tab->
                    val current=c.activeId==tab.id
                    Surface(onClick={c.switchTab(tab.id)},shape=RoundedCornerShape(18.dp),color=colors.surface,border=BorderStroke(if(current)2.dp else 1.dp,if(current)colors.primary else colors.outlineVariant.copy(alpha=.45f)),modifier=Modifier.fillMaxWidth().testTag("tab-card:${tab.id}").semantics{selected=current}){
                        Column{
                            Row(Modifier.fillMaxWidth().padding(start=12.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically){
                                val icon=tab.pageIcon
                                if(!private&&icon!=null&&!icon.isRecycled)Image(icon.asImageBitmap(),null,Modifier.size(18.dp))
                                else Icon(if(private)Icons.Outlined.VisibilityOff else Icons.Outlined.Language,null,Modifier.size(18.dp),tint=colors.primary)
                                Text(if(current)"目前分頁"else if(private)"無痕分頁"else"網頁",Modifier.weight(1f).padding(start=7.dp),fontSize=11.sp,color=colors.onSurfaceVariant,maxLines=1)
                                IconButton(onClick={c.closeTab(tab.id)},modifier=Modifier.size(40.dp).testTag("close-tab:${tab.id}")){Icon(Icons.Outlined.Close,"關閉 ${tab.title}",Modifier.size(18.dp))}
                            }
                            Surface(Modifier.fillMaxWidth().aspectRatio(1.55f).padding(horizontal=8.dp),shape=RoundedCornerShape(11.dp),color=colors.background){
                                val image=tab.preview
                                if(!private&&image!=null&&!image.isRecycled)Image(image.asImageBitmap(),"網頁預覽",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                                        Icon(if(private)Icons.Outlined.VisibilityOff else Icons.Outlined.Public,null,Modifier.size(30.dp),tint=colors.primary.copy(alpha=.55f))
                                        Text(if(private)"不擷取頁面預覽"else if(tab.url.isBlank())"澄境"else Domains.scope(tab.url),fontSize=11.sp,color=colors.onSurfaceVariant,maxLines=1,modifier=Modifier.padding(horizontal=8.dp),overflow=TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(tab.title.ifBlank{"新分頁"},fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                                Text(Domains.scope(tab.url).ifBlank{if(private)"私人瀏覽"else"澄境首頁"},fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(private&&c.tabs.any{it.incognito})TextButton(onClick={closePrivate=true},modifier=Modifier.testTag("close-all-private")){Text("全部關閉")}
                Button(onClick={if(c.newTab(incognito=private)!=null)c.sheet=""},enabled=!private||c.privateSession.supported,modifier=Modifier.weight(1f).height(48.dp).testTag("new-tab-in-collection")){
                    Icon(Icons.Outlined.Add,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text(if(private)"新增無痕分頁"else"新增分頁")
                }
            }
        }
    }
    if(closePrivate)AlertDialog(onDismissRequest={closePrivate=false},title={Text("關閉全部無痕分頁？")},text={Text("這次的無痕瀏覽將結束。手動儲存的下載、書籤及收藏仍會保留。")},confirmButton={TextButton(onClick={c.tabs.filter{it.incognito}.map{it.id}.forEach{c.closeTab(it)};closePrivate=false}){Text("全部關閉")}},dismissButton={TextButton(onClick={closePrivate=false}){Text("取消")}})
}

@Composable internal fun PrivateModeStrip(){
    Surface(color=MaterialTheme.colorScheme.surfaceVariant){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=7.dp).testTag("private-indicator"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Outlined.VisibilityOff,null,Modifier.size(15.dp))
            Text("無痕瀏覽 · 不加入歷史記錄",fontSize=11.sp)
        }
    }
}
@Composable internal fun PrivateStartPage(){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(30.dp),verticalArrangement=Arrangement.Center){
        Icon(Icons.Outlined.VisibilityOff,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("留白，\n給這一次瀏覽。",fontSize=30.sp,lineHeight=42.sp,fontWeight=FontWeight.Light)
        Spacer(Modifier.height(18.dp))
        Text("不保存歷史、搜尋詞或重開分頁。網站 Cookie 與儲存空間和一般分頁分開，關閉全部無痕分頁後清除。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("無痕不是匿名上網。網站、網路管理者及網路服務商仍可能辨識你的連線。手動下載、書籤、收藏及網站規則仍會保留。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
