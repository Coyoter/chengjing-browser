package tw.techtarian.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LanguageSettings(activity:MainActivity){
    var expanded by remember{mutableStateOf(false)}
    val choice=AppLanguages.choice
    val current=AppLanguagePolicy.autonym(AppLanguages.currentTag)
    SettingsGroup(bt(R.string.language_title)){
        ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){
            OutlinedTextField(
                value=if(choice=="system")bt(R.string.language_system)else AppLanguagePolicy.autonym(choice),
                onValueChange={},readOnly=true,singleLine=true,
                modifier=Modifier.fillMaxWidth().menuAnchor().testTag("language-picker"),
                shape=RoundedCornerShape(18.dp),
                textStyle=TextStyle(textDirection=TextDirection.Content),
                leadingIcon={Icon(Icons.Outlined.Language,null)},
                trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},
                colors=ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded=expanded,onDismissRequest={expanded=false},
                modifier=Modifier.heightIn(max=360.dp),
                shape=RoundedCornerShape(18.dp),
                containerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation=0.dp,shadowElevation=10.dp,
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.45f)),
            ){
                val options=listOf(AppLanguage("system",bt(R.string.language_system)))+AppLanguagePolicy.languages
                options.forEach{language->
                    DropdownMenuItem(
                        text={Text(language.autonym,style=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Content))},
                        onClick={expanded=false;activity.applyLanguage(language.tag)},
                        modifier=Modifier.heightIn(min=52.dp)
                            .background(if(choice==language.tag)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .testTag("language:${language.tag}"),
                        trailingIcon={if(choice==language.tag)Icon(Icons.Outlined.Check,null,tint=MaterialTheme.colorScheme.primary)},
                    )
                }
            }
        }
        Text(if(choice=="system")bt(R.string.language_system_detail,current)else bt(R.string.language_selected,current),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("language-summary"))
        Text(bt(R.string.language_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
