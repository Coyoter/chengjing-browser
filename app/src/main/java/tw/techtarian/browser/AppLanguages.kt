package tw.techtarian.browser

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Local preference only; never included in Google sync. All translations are bundled offline. */
internal object AppLanguages {
    var choice by mutableStateOf("system");private set
    var currentTag by mutableStateOf("zh-TW");private set
    @Volatile private var localized:Context?=null
    private fun preferences(context:Context)=context.getSharedPreferences("browser-v1",Context.MODE_PRIVATE)
    fun systemTag(context:Context):String {
        val locales=LocaleManagerCompat.getSystemLocales(context)
        return AppLanguagePolicy.resolve((0 until locales.size()).mapNotNull{locales[it]})
    }
    fun initialize(context:Context){refresh(context,readExternal=true)}
    fun select(context:Context,value:String){
        require(AppLanguagePolicy.validChoice(value))
        preferences(context).edit().putString("app-language",value).apply()
        choice=value
        apply(context,if(value=="system")systemTag(context)else value)
    }
    fun refresh(context:Context,readExternal:Boolean=true){
        val prefs=preferences(context)
        val stored=prefs.getString("app-language","system")
        val framework=AppCompatDelegate.getApplicationLocales()
        val frameworkTag=framework[0]?.let{AppLanguagePolicy.match(it)}
        val last=prefs.getString("language-applied",null)
        // Before Android 13 AppCompat's locales are process-local, so empty at cold start.
        // Only the persisted system per-app language can override our saved preference.
        val saved=AppLanguagePolicy.restoreChoice(stored,last,frameworkTag,!framework.isEmpty,
            readExternal&&android.os.Build.VERSION.SDK_INT>=33)
        if(saved!=stored)prefs.edit().putString("app-language",saved).apply()
        choice=saved
        apply(context,if(saved=="system")systemTag(context)else saved)
    }
    private fun apply(context:Context,tag:String){
        val locale=Locale.forLanguageTag(tag)
        val config=Configuration(context.applicationContext.resources.configuration).apply{setLocale(locale);setLayoutDirection(locale)}
        localized=context.applicationContext.createConfigurationContext(config)
        currentTag=tag
        Locale.setDefault(locale)
        preferences(context).edit().putString("language-applied",tag).apply()
        if(android.os.Build.VERSION.SDK_INT>=33){
            // This also works before the first Activity delegate exists (including QA startup).
            val manager=context.getSystemService(android.app.LocaleManager::class.java)
            if(manager.applicationLocales.toLanguageTags()!=tag)
                manager.applicationLocales=android.os.LocaleList.forLanguageTags(tag)
        }else if(AppCompatDelegate.getApplicationLocales().toLanguageTags()!=tag){
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
    fun resources():Resources? { currentTag;return localized?.resources }
}
