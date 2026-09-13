package tw.techtarian.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.util.Locale

data class AddressSuggestion(val title:String,val subtitle:String,val input:String,val search:Boolean)
object AddressHistory {
    private fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    fun suggestions(input:String,searches:List<String>,pages:List<Pair<String,String>>):List<AddressSuggestion>{
        val query=normalize(input);val terms=query.split(Regex("\\s+")).filter{it.isNotEmpty()}
        val rows=searches.map{AddressSuggestion(it,"再次搜尋",it,true)}+pages.map{(url,title)->AddressSuggestion(title.ifBlank{url},url,url,false)}
        return rows.distinctBy{it.search to it.input}.filter{row->val hay=normalize(row.title+" "+row.subtitle);terms.all{it in hay}}
            .sortedByDescending{row->val label=normalize(row.title);when{query.isEmpty()->0;label==query->3;label.startsWith(query)->2;query in label->1;else->0}}.take(6)
    }
}
@Composable internal fun AddressSuggestionPanel(c:BrowserController,rows:List<AddressSuggestion>,onSelect:(String)->Unit){
    if(rows.isEmpty())return
    Surface(Modifier.fillMaxWidth().testTag("address-suggestions"),color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp){
        Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=4.dp)){
            rows.forEachIndexed{i,row->
                Row(Modifier.fillMaxWidth().height(54.dp).testTag("address-suggestion-$i").clickable{onSelect(row.input)},verticalAlignment=Alignment.CenterVertically){
                    if(row.search)Icon(Icons.Outlined.Search,null,Modifier.size(32.dp).padding(5.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)else SiteIcon(c,row.input,32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(row.title,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(row.subtitle,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
