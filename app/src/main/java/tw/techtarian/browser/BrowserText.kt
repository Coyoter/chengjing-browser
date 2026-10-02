package tw.techtarian.browser

import androidx.annotation.StringRes
import java.util.Locale

/** Resource-backed text shared by Compose, native prompts, async notices and validation errors. */
internal fun bt(@StringRes resource:Int,vararg arguments:Any?):String {
    val tag=AppLanguages.currentTag
    val text=AppLanguages.resources()?.getString(resource)?:BrowserTextSource.values[resource].orEmpty()
    val values=arguments.map{if(it is BrowserCaption)it.text()else it}.toTypedArray()
    return if(values.isEmpty())text else String.format(Locale.forLanguageTag(tag),text,*values)
}

/** Long-lived UI status retains resource IDs, so changing language never leaves a stale caption. */
internal class BrowserCaption(private val render:()->String){
    fun text()=render()
    operator fun plus(other:BrowserCaption)=BrowserCaption{text()+other.text()}
    companion object{fun literal(text:String)=BrowserCaption{text}}
}
internal fun bcaption(@StringRes resource:Int,vararg arguments:Any?)=BrowserCaption{bt(resource,*arguments)}
