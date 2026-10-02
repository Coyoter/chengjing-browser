package tw.techtarian.browser

import java.util.Locale

internal data class AppLanguage(val tag:String,val autonym:String)

/** Pure matching policy: the first supported phone language wins, otherwise English. */
internal object AppLanguagePolicy {
    val languages=listOf(
        AppLanguage("zh-TW","繁體中文"),AppLanguage("zh-CN","简体中文"),AppLanguage("en","English"),
        AppLanguage("ja","日本語"),AppLanguage("ko","한국어"),AppLanguage("fr","Français"),
        AppLanguage("de","Deutsch"),AppLanguage("es","Español"),AppLanguage("pt","Português"),
        AppLanguage("ar","العربية"),AppLanguage("th","ไทย"),AppLanguage("ru","Русский"),
        AppLanguage("hi","हिन्दी"),AppLanguage("id","Bahasa Indonesia"),AppLanguage("vi","Tiếng Việt"),
        AppLanguage("bn","বাংলা"),AppLanguage("ur","اردو"),
    )
    fun match(locale:Locale):String?=when(locale.language.lowercase(Locale.ROOT)){
        "zh"->if(locale.script.equals("Hant",true)||locale.script.isEmpty()&&locale.country.uppercase(Locale.ROOT) in setOf("TW","HK","MO"))"zh-TW"else"zh-CN"
        "in","id"->"id"
        else->languages.firstOrNull{it.tag==locale.language.lowercase(Locale.ROOT)}?.tag
    }
    fun resolve(locales:List<Locale>):String=locales.firstNotNullOfOrNull{match(it)}?:"en"
    fun validChoice(value:String)=value=="system"||languages.any{it.tag==value}
    fun restoreChoice(saved:String?,last:String?,frameworkTag:String?,frameworkHasChoice:Boolean,readSystemChanges:Boolean):String {
        val local=saved?.takeIf{validChoice(it)}?:"system"
        return if(readSystemChanges&&((last==null&&frameworkHasChoice)||(last!=null&&frameworkTag!=last)))
            frameworkTag?:"system" else local
    }
    fun autonym(tag:String)=languages.firstOrNull{it.tag==tag}?.autonym?:"English"
    fun isRtl(tag:String)=tag in setOf("ar","ur")
    fun aiLanguage(tag:String)=when(tag){
        "zh-TW"->"Traditional Chinese";"zh-CN"->"Simplified Chinese";"ja"->"Japanese";"ko"->"Korean"
        "fr"->"French";"de"->"German";"es"->"Spanish";"pt"->"Portuguese";"ar"->"Arabic";"th"->"Thai"
        "ru"->"Russian";"hi"->"Hindi";"id"->"Indonesian";"vi"->"Vietnamese";"bn"->"Bengali";"ur"->"Urdu";else->"English"
    }
}
