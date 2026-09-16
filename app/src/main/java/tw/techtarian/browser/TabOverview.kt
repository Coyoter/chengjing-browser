package tw.techtarian.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable internal fun ColumnScope.TabOverview(c:BrowserController) {
    val colors=MaterialTheme.colorScheme
    val privateGroup=c.overviewPrivate
    val tabs=c.tabs.filter{it.incognito==privateGroup}
    var confirmClose by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(false to "一般",true to "無痕").forEach{(private,label)->
            val selected=privateGroup==private
            Surface(onClick={c.overviewPrivate=private;c.updatePrivacyWindow()},modifier=Modifier.weight(1f).height(48.dp).testTag("tab-group-$private").semantics{this.selected=selected;role=Role.Tab},
                shape=RoundedCornerShape(14.dp),color=if(selected)colors.primaryContainer else colors.surface) {
                Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    Icon(if(private)Icons.Outlined.PrivacyTip else Icons.Outlined.Tab,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp));Text("$label ${c.tabs.count{it.incognito==private}}",fontWeight=FontWeight.Medium)
                }
            }
        }
    }
    if(privateGroup)Text("無痕頁面不產生縮圖、不加入歷史記錄。關閉全部無痕分頁後清理網站資料。",Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=12.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
    if(tabs.isEmpty())CollectionEmpty(if(privateGroup)"開啟一段無痕瀏覽"else"還沒有一般分頁",if(privateGroup)"與一般分頁使用不同的登入狀態；網站與網路供應商仍可能辨識你的活動。"else"新增分頁，讓接下來的閱讀有自己的位置。")
    LazyVerticalGrid(GridCells.Adaptive(150.dp),Modifier.weight(1f).fillMaxWidth().testTag("tab-grid"),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        items(tabs,key={it.id}){tab->
            val current=tab.id==c.activeId
            Surface(onClick={c.switchTab(tab.id)},modifier=Modifier.fillMaxWidth().testTag("tab-card-${tab.id}").semantics{selected=current},shape=RoundedCornerShape(20.dp),
                color=colors.surface,border=if(current)BorderStroke(1.5.dp,colors.primary)else BorderStroke(1.dp,colors.outlineVariant.copy(alpha=.5f))) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(colors.surfaceVariant.copy(alpha=.55f))) {
                        val preview=tab.preview
                        if(!tab.incognito&&preview!=null&&!preview.isRecycled)Image(preview.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        else Column(Modifier.align(Alignment.Center).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(if(tab.incognito)Icons.Outlined.PrivacyTip else Icons.Outlined.Language,null,Modifier.size(30.dp),tint=colors.primary.copy(alpha=.6f))
                            Text(if(tab.incognito)"私密頁面"else if(tab.url.isEmpty())"澄境首頁"else Domains.scope(tab.url),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                        }
                        Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),shape=RoundedCornerShape(50),color=colors.surface.copy(alpha=.95f)){
                            IconButton(onClick={c.closeTab(tab.id)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Close,"關閉分頁 ${tab.id}",Modifier.size(19.dp))}
                        }
                    }
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(tab.title.ifBlank{"新分頁"},minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium)
                        val site=Domains.scope(tab.url).ifBlank{"澄境首頁"}
                        Text(if(current)"目前分頁 · $site"else site,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(current)colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(privateGroup&&tabs.isNotEmpty())TextButton(onClick={confirmClose=true}){Text("全部關閉")}
        Button(onClick={if(c.newTab(incognito=privateGroup)!=null)c.sheet=""},modifier=Modifier.weight(1f).height(50.dp),shape=RoundedCornerShape(16.dp)){
            Icon(Icons.Outlined.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(privateGroup)"新增無痕分頁"else"新增分頁")
        }
    }
    if(confirmClose)AlertDialog(onDismissRequest={confirmClose=false},title={Text("關閉全部無痕分頁？")},text={Text("這些分頁無法復原。已主動下載的檔案與儲存的書籤、收藏仍會保留。")},confirmButton={TextButton(onClick={c.closePrivateTabs();confirmClose=false}){Text("全部關閉")}},dismissButton={TextButton(onClick={confirmClose=false}){Text("取消")}})
}

@Composable internal fun IncognitoHome() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(30.dp))
        Icon(Icons.Outlined.PrivacyTip,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
        Text("此刻，只留在此刻。",fontSize=29.sp,lineHeight=40.sp,fontWeight=FontWeight.Light)
        Text("無痕瀏覽",style=MaterialTheme.typography.titleMedium)
        Text("不儲存瀏覽與搜尋記錄，不復原無痕分頁。登入狀態與一般分頁分開，關閉全部無痕分頁後清理 Cookie、快取與網站資料。",fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("無痕不等於匿名。網站、雇主或網路供應商仍可能看到你的活動。你主動下載的檔案、儲存的書籤與收藏會保留；系統分享及 AI 請求仍會把選擇的內容交給相關服務。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
