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
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(false to bt(R.string.msg_91e25f4ddc6f),true to bt(R.string.msg_d0d9f720ed65)).forEach{(private,label)->
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
    if(privateGroup)Text(bt(R.string.msg_87c6a9d4a825),Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=12.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
    if(tabs.isEmpty())CollectionEmpty(if(privateGroup)bt(R.string.msg_b7c8e18bb813)else bt(R.string.msg_de2a64702660),if(privateGroup)bt(R.string.msg_bae05c66c03b)else bt(R.string.msg_8e0ae72065d1))
    LazyVerticalGrid(GridCells.Adaptive(150.dp),Modifier.weight(1f).fillMaxWidth().testTag("tab-grid"),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        items(tabs,key={it.id}){tab->
            val current=tab.id==c.activeId
            Surface(onClick={c.switchTab(tab.id)},modifier=Modifier.fillMaxWidth().testTag("tab-card-${tab.id}").semantics{selected=current},shape=RoundedCornerShape(20.dp),
                color=colors.surface,border=if(current)BorderStroke(1.5.dp,colors.primary)else BorderStroke(1.dp,colors.outlineVariant.copy(alpha=.5f))) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(colors.surfaceVariant.copy(alpha=.55f))) {
                        val preview=tab.preview
                        if(!tab.incognito&&preview!=null&&!preview.isRecycled)Image(preview.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alignment=Alignment.TopCenter)
                        else Column(Modifier.align(Alignment.Center).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(if(tab.incognito)Icons.Outlined.PrivacyTip else Icons.Outlined.Language,null,Modifier.size(30.dp),tint=colors.primary.copy(alpha=.6f))
                            Text(if(tab.incognito)bt(R.string.msg_89d55dea3269)else if(tab.url.isEmpty())bt(R.string.msg_01e143a4ff67)else Domains.scope(tab.url),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                        }
                        Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),shape=RoundedCornerShape(50),color=colors.surface.copy(alpha=.95f)){
                            IconButton(onClick={c.closeTab(tab.id)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Close,bt(R.string.msg_3cea311ebc49 ,tab.id),Modifier.size(19.dp))}
                        }
                    }
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(tab.title.ifBlank{bt(R.string.msg_052052ba842e)},minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium)
                        val site=Domains.scope(tab.url).ifBlank{bt(R.string.msg_01e143a4ff67)}
                        Text(if(current)bt(R.string.msg_91ce40bce8fd ,site)else site,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(current)colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().testTag("tab-overview-footer").padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={if(c.newTab(incognito=privateGroup)!=null)c.sheet=""},modifier=Modifier.weight(1f).height(50.dp).testTag("new-overview-tab"),shape=RoundedCornerShape(16.dp)){
            Icon(Icons.Outlined.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(privateGroup)bt(R.string.msg_ce7454d53ff8)else bt(R.string.msg_a38d62ae74d4))
        }
    }

}

@Composable internal fun IncognitoHome() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(30.dp))
        Icon(Icons.Outlined.PrivacyTip,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
        Text(bt(R.string.msg_71ab24c40404),fontSize=29.sp,lineHeight=40.sp,fontWeight=FontWeight.Light)
        Text(bt(R.string.msg_79606cdda212),style=MaterialTheme.typography.titleMedium)
        Text(bt(R.string.msg_ec4537100d5f),fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(bt(R.string.msg_c4759e2fd146),fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
