package tw.techtarian.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class BrowserMenuAction(val label:String,val icon:ImageVector,val run:()->Unit)
internal sealed class BrowserPrompt(
    val title:String,val owner:Int?,val valid:()->Boolean,val onCancel:()->Unit
) {
    class Menu(title:String,val subtitle:String,val actions:List<BrowserMenuAction>,owner:Int,
               valid:()->Boolean):BrowserPrompt(title,owner,valid,{})
    class Confirm(title:String,val message:String,val confirmLabel:String,val confirm:()->Unit,
                  val cancelLabel:String,owner:Int?,valid:()->Boolean,onCancel:()->Unit,
                  val input:String?=null,val submit:((String)->Unit)?=null,
                  val showCancel:Boolean=true):BrowserPrompt(title,owner,valid,onCancel)
}

/** All app-owned prompts render under the same Material theme, including async callbacks. */
internal class BrowserPrompts {
    var current by mutableStateOf<BrowserPrompt?>(null);private set
    fun show(prompt:BrowserPrompt){cancel();current=prompt}
    fun cancel(expected:BrowserPrompt?=current){
        if(expected==null||current!==expected)return
        current=null;expected.onCancel()
    }
    fun act(prompt:BrowserPrompt,action:()->Unit){
        if(current!==prompt)return
        if(!prompt.valid()){cancel(prompt);return}
        current=null;action()
    }
    fun closeFor(owner:Int){current?.takeIf{it.owner==owner}?.let{cancel(it)}}
    fun confirm(title:String,message:String,confirmLabel:String,owner:Int?=null,
                valid:()->Boolean={true},onCancel:()->Unit={},cancelLabel:String="取消",confirm:()->Unit){
        show(BrowserPrompt.Confirm(title,message,confirmLabel,confirm,cancelLabel,owner,valid,onCancel))
    }
}

@Composable internal fun BrowserPromptHost(c:BrowserController){
    val prompt=c.prompts.current?:return
    LaunchedEffect(prompt,c.activeId,c.active?.url,c.active?.navigationGeneration){
        if(!prompt.valid())c.prompts.cancel(prompt)
    }
    when(prompt){
        is BrowserPrompt.Menu->BrowserActionMenu(prompt,{c.prompts.cancel(prompt)}){c.prompts.act(prompt,it)}
        is BrowserPrompt.Confirm->{
            var input by remember(prompt){mutableStateOf(prompt.input.orEmpty())}
            AlertDialog(
                onDismissRequest={c.prompts.cancel(prompt)},
                modifier=Modifier.testTag("browser-confirmation"),
                containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation=0.dp,
                title={Text(prompt.title)},
                text={Column(Modifier.heightIn(max=(LocalConfiguration.current.screenHeightDp*.5f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Text(prompt.message)
                    if(prompt.input!=null)OutlinedTextField(input,{input=it},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("website-prompt-input"),label={Text("輸入內容")})
                }},
                confirmButton={TextButton(onClick={c.prompts.act(prompt){prompt.submit?.invoke(input)?:prompt.confirm()}}){Text(prompt.confirmLabel)}},
                dismissButton={if(prompt.showCancel)TextButton(onClick={c.prompts.cancel(prompt)}){Text(prompt.cancelLabel)}}
            )
        }
    }
}

@Composable private fun BrowserActionMenu(prompt:BrowserPrompt.Menu,dismiss:()->Unit,run:(()->Unit)->Unit){
    val colors=MaterialTheme.colorScheme
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(
            Modifier.padding(24.dp).widthIn(max=560.dp).fillMaxWidth()
                .heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp).testTag("page-context-menu"),
            shape=RoundedCornerShape(28.dp),color=colors.surfaceContainerHigh,
            contentColor=colors.onSurface,tonalElevation=0.dp
        ){
            Column {
                Row(Modifier.fillMaxWidth().padding(start=24.dp,top=16.dp,end=8.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(prompt.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(prompt.subtitle.isNotBlank())Text(prompt.subtitle,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick=dismiss){Icon(Icons.Outlined.Close,"關閉長按選單")}
                }
                HorizontalDivider(color=colors.outlineVariant.copy(alpha=.6f))
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(vertical=8.dp)){
                    prompt.actions.forEach{item->
                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{run(item.run)}
                            .padding(horizontal=24.dp,vertical=12.dp).testTag("context-action:${item.label}"),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                            Icon(item.icon,null,Modifier.size(22.dp),tint=colors.primary)
                            Text(item.label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
