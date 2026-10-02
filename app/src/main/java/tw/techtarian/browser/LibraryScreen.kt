package tw.techtarian.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date

@Composable internal fun LibraryScreen(c:BrowserController){
    val favoritesMode=c.sheet=="favorites"
    val revision=c.revision
    val books=remember(revision){c.store.bookmarkStore.visible()}
    val tree=remember(books){BookmarkTree(books)}
    val favorites=remember(revision){c.favorites.all()}
    var path by remember(favoritesMode){mutableStateOf<List<String>?>(emptyList())}
    var query by remember(favoritesMode){mutableStateOf("")}
    var editingBook by remember{mutableStateOf<Bookmark?>(null)}
    var editingFavorite by remember{mutableStateOf<Favorite?>(null)}
    var editTitle by remember{mutableStateOf("")}
    var editFolder by remember{mutableStateOf("")}
    var editUrl by remember{mutableStateOf("")}
    var editError by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(c){snapshotFlow{c.notice}.filter{it.isNotEmpty()}.collectLatest{message->c.notice="";snackbar.showSnackbar(message)}}
    val colors=MaterialTheme.colorScheme
    val back:()->Unit={when{query.isNotEmpty()->query="";!favoritesMode&&path==null->path=emptyList();!favoritesMode&&!path.isNullOrEmpty()->path=path!!.dropLast(1);else->c.sheet=""}}
    Dialog(onDismissRequest=back,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        val view=LocalView.current
        SideEffect{
            (view.parent as? DialogWindowProvider)?.window?.let{window->
                browserSystemBars(window,view,colors.background.luminance()>.5f)
            }
        }
        Surface(Modifier.fillMaxSize().testTag("library-screen"),color=colors.background){
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
                    Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically){
                        if(!favoritesMode&&(path==null||!path.isNullOrEmpty()))IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,bt(R.string.msg_d5aecc70e422))}
                        Text(if(favoritesMode)bt(R.string.msg_60a53514eb92)else if(path==null)bt(R.string.msg_607089b311be)else path!!.lastOrNull()?:bt(R.string.msg_1eb1e5b30e73),Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(!favoritesMode)IconButton(onClick={(c.context as MainActivity).importBookmarks()}){Icon(Icons.Outlined.FileDownload,bt(R.string.msg_e371aca7156b))}
                        IconButton(onClick={c.sheet=""}){Icon(Icons.Outlined.Close,if(favoritesMode)bt(R.string.msg_901fcc465532)else bt(R.string.msg_714be9fccc36))}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(selected=!favoritesMode,onClick={c.sheet="bookmarks"},label={Text(bt(R.string.msg_1eb1e5b30e73))},leadingIcon={Icon(Icons.Outlined.Folder,null,Modifier.size(18.dp))})
                        FilterChip(selected=favoritesMode,onClick={c.sheet="favorites"},label={Text(bt(R.string.msg_60a53514eb92))},leadingIcon={Icon(Icons.Outlined.StarOutline,null,Modifier.size(18.dp))})
                        Spacer(Modifier.weight(1f))
                        Text(if(favoritesMode)bt(R.string.msg_d1e037d6f243 ,favorites.size)else bt(R.string.msg_b4016776e423 ,books.size),Modifier.align(Alignment.CenterVertically),fontSize=12.sp,color=colors.onSurfaceVariant)
                    }
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp).testTag("library-search"),singleLine=true,placeholder={Text(if(favoritesMode)bt(R.string.msg_6200fe0b2272)else bt(R.string.msg_f9f0c177bd9a))},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,bt(R.string.msg_0c2d1a3aeaff))}},shape=RoundedCornerShape(18.dp))
                    if(favoritesMode){
                        val linked=c.active?.favoriteId?.let{c.favorites.get(it)}
                        if(c.domain.isNotEmpty()){
                            Button(onClick={scope.launch{if(c.saveFavorite()!=null)snackbar.showSnackbar(if(linked==null)bt(R.string.msg_4220592340ad)else bt(R.string.msg_2aedd851c6ea))}},Modifier.fillMaxWidth()){
                                Icon(Icons.Outlined.StarOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(linked==null)bt(R.string.msg_3e40d2036259)else bt(R.string.msg_e49810bb4ecf))
                            }
                            if(linked!=null)TextButton(onClick={scope.launch{if(c.saveFavorite(true)!=null)snackbar.showSnackbar(bt(R.string.msg_fcedb76bd215))}},Modifier.align(Alignment.End)){Text(bt(R.string.msg_74894a77754e))}
                        }
                        Text(bt(R.string.msg_af1f0a81d5e6),Modifier.padding(vertical=10.dp),fontSize=12.sp,color=colors.onSurfaceVariant)
                        val rows=favorites.filter{"${it.title} ${it.pageTitle} ${it.url}".contains(query.trim(),true)}
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("favorite-list"),contentPadding=PaddingValues(bottom=24.dp)){
                            if(rows.isEmpty())item{EmptyLibrary(if(query.isBlank())bt(R.string.msg_6d9532de6549)else bt(R.string.msg_9c88b8194478),if(query.isBlank())bt(R.string.msg_8c9ca0c6c1a7)else bt(R.string.msg_299775d315e8))}
                            items(rows,key={it.id}){favorite->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable{c.openFavorite(favorite)}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                                    SiteIcon(c,favorite.url,44.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)){
                                        Text(favorite.title,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                        if(favorite.pageTitle!=favorite.title)Text(favorite.pageTitle,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                                        Text(bt(R.string.msg_703e48693dc4 ,Domains.scope(favorite.url),(favorite.progress*100).toInt()),fontSize=12.sp,color=colors.onSurfaceVariant)
                                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(favorite.updated)),fontSize=11.sp,color=colors.onSurfaceVariant)
                                    }
                                    IconButton(onClick={editingFavorite=favorite;editTitle=favorite.title;editError=""}){Icon(Icons.Outlined.Edit,bt(R.string.msg_f8e14a0fde6e ,favorite.title),Modifier.size(19.dp))}
                                    IconButton(onClick={c.removeFavorite(favorite.id)}){Icon(Icons.Outlined.Close,bt(R.string.msg_27e77e5a18e0 ,favorite.title),Modifier.size(19.dp))}
                                }
                            }
                        }
                    }else{
                        if(query.isBlank() && (path==null||!path.isNullOrEmpty()))Text(bt(R.string.msg_c8e5535d82eb)+(path?.joinToString(" › ")?:bt(R.string.msg_607089b311be)),Modifier.padding(bottom=12.dp),fontSize=12.sp,color=colors.onSurfaceVariant)
                        val node=path?.let{tree.node(it)}
                        val rows=if(query.isNotBlank())books.filter{"${it.title} ${it.url} ${it.folder}".contains(query.trim(),true)}else if(path==null)tree.root.bookmarks else if(path!!.isEmpty())emptyList()else node?.bookmarks.orEmpty()
                        val folders=if(query.isBlank())node?.children?.values?.toList().orEmpty()else emptyList()
                        val rootUnfiled=query.isBlank()&&path!=null&&path!!.isEmpty()&&tree.root.bookmarks.isNotEmpty()
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("bookmark-list"),contentPadding=PaddingValues(bottom=24.dp)){
                            if(folders.isEmpty()&&rows.isEmpty()&&!rootUnfiled)item{EmptyLibrary(if(query.isNotBlank())bt(R.string.msg_672959cfc33b)else bt(R.string.msg_b682d0e78eab),if(query.isNotBlank())bt(R.string.msg_076e5d656ed2)else bt(R.string.msg_a581a0c5331f))}
                            items(folders,key={"folder:"+it.path.joinToString(" / ")}){folder->FolderRow(folder.name,folder.total){path=folder.path}}
                            if(rootUnfiled)item(key="unfiled"){FolderRow(bt(R.string.msg_607089b311be),tree.root.bookmarks.size){path=null}}
                            items(rows,key={it.id}){bookmark->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                                    SiteIcon(c,bookmark.url)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f).clickable{c.navigate(bookmark.url)}.padding(vertical=12.dp)){
                                        Text(bookmark.title,fontSize=15.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                        Text(if(query.isNotBlank())bookmark.folder.ifEmpty{bt(R.string.msg_607089b311be)}+" · "+Domains.scope(bookmark.url)else Domains.scope(bookmark.url),fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                                    }
                                    IconButton(onClick={editingBook=bookmark;editTitle=bookmark.title;editFolder=bookmark.folder;editUrl=bookmark.url;editError=""}){Icon(Icons.Outlined.Edit,bt(R.string.msg_11ae55de6d31 ,bookmark.title),Modifier.size(19.dp))}
                                    IconButton(onClick={c.store.bookmarkStore.remove(bookmark.id);c.revision++}){Icon(Icons.Outlined.Close,bt(R.string.msg_769359dc3c3e ,bookmark.title),Modifier.size(19.dp))}
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically){
                            if(c.domain.isNotEmpty())TextButton(onClick={
                                val count=c.store.bookmarkStore.add(c.active!!.url,c.active!!.title,path?.joinToString(" / ").orEmpty())
                                c.revision++;scope.launch{snackbar.showSnackbar(if(count>0)bt(R.string.msg_8fe34ed575cd)else bt(R.string.msg_8793a7ffa47a))}
                            }){Icon(Icons.Outlined.BookmarkAdd,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(bt(R.string.msg_6cd6b2d898cc))}
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick={(c.context as MainActivity).exportBookmarks()}){Text(bt(R.string.msg_c631952bb114))}
                            IconButton(onClick={c.sheet="sync"}){Icon(Icons.Outlined.CloudSync,bt(R.string.msg_4d94c28a53c4))}
                        }
                    }
                }
                SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        if(editingBook!=null||editingFavorite!=null){
            val urlError=if(editingBook!=null)runCatching{BookmarkFormat.editedUrl(editUrl)}.exceptionOrNull()?.localizedMessage else null
            AlertDialog(onDismissRequest={editingBook=null;editingFavorite=null},title={Text(if(editingBook!=null)bt(R.string.msg_27001df08520)else bt(R.string.msg_1b98d866b7c9))},text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedTextField(editTitle,{editTitle=it;editError=""},modifier=Modifier.fillMaxWidth().testTag("bookmark-edit-title"),label={Text(bt(R.string.msg_a5274f381305))},singleLine=true)
                if(editingBook!=null){
                    OutlinedTextField(editUrl,{editUrl=it;editError=""},modifier=Modifier.fillMaxWidth().testTag("bookmark-edit-url"),label={Text(bt(R.string.msg_c09d6f8a398c))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,autoCorrectEnabled=false),isError=urlError!=null,supportingText=if(urlError!=null)({Text(urlError)})else null)
                    OutlinedTextField(editFolder,{editFolder=it;editError=""},modifier=Modifier.fillMaxWidth().testTag("bookmark-edit-folder"),label={Text(bt(R.string.msg_aabc6623539f))},placeholder={Text(bt(R.string.msg_a2c701b369be))},supportingText={Text(bt(R.string.msg_b631887c06ef))})
                }
                if(editError.isNotEmpty())Text(editError,color=colors.error,fontSize=12.sp)
            }},confirmButton={TextButton(enabled=editTitle.isNotBlank()&&urlError==null,onClick={
                runCatching{
                    editingBook?.let{c.store.bookmarkStore.edit(it.id,editTitle,editFolder,editUrl)}
                    editingFavorite?.let{c.favorites.rename(it.id,editTitle)}
                }.onSuccess{editingBook=null;editingFavorite=null;c.revision++}
                    .onFailure{editError=it.localizedMessage?:bt(R.string.msg_978626066ea1)}
            }){Text(bt(R.string.msg_dba44ed3f54f))}},dismissButton={TextButton(onClick={editingBook=null;editingFavorite=null}){Text(bt(R.string.msg_2cd0f3be8738))}})
        }
    }
}

@Composable private fun FolderRow(name:String,count:Int,onClick:()->Unit){
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Surface(shape=RoundedCornerShape(16.dp),color=colors.primaryContainer,modifier=Modifier.size(58.dp)){Box(contentAlignment=Alignment.Center){Icon(Icons.Outlined.FolderOpen,null,Modifier.size(27.dp),tint=colors.primary)}}
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)){Text(name,fontSize=17.sp,fontWeight=FontWeight.Medium);Text(bt(R.string.msg_b4016776e423 ,count),fontSize=12.sp,color=colors.onSurfaceVariant)}
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=colors.onSurfaceVariant)
    }
}
@Composable private fun EmptyLibrary(title:String,detail:String){Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontSize=17.sp,fontWeight=FontWeight.Medium);Text(detail,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
