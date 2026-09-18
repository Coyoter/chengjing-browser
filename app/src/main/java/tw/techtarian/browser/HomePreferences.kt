package tw.techtarian.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Device-local settings, separate from bookmarks/Google sync and site rules. */
internal class HomePreferences(context:Context,existingUser:Boolean=false) {
    private val prefs=context.getSharedPreferences("home-v1",Context.MODE_PRIVATE)
    init {
        if(!prefs.contains("intro-seen"))check(prefs.edit().putBoolean("intro-seen",existingUser).commit())
    }
    var enabled by mutableStateOf(prefs.getBoolean("button-enabled",false))
        private set
    var custom by mutableStateOf(prefs.getBoolean("custom-home",false))
        private set
    var url by mutableStateOf(prefs.getString("home-url","").orEmpty())
        private set
    fun updateEnabled(value:Boolean){
        check(prefs.edit().putBoolean("button-enabled",value).commit())
        enabled=value
    }
    fun useDefault(){
        check(prefs.edit().putBoolean("custom-home",false).commit())
        custom=false
    }
    fun useCustom(value:String):Boolean {
        val normalized=HomePolicy.normalizeUrl(value)?:return false
        check(prefs.edit().putString("home-url",normalized).putBoolean("custom-home",true).commit())
        url=normalized;custom=true
        return true
    }
    fun destination():String?=if(enabled&&custom)HomePolicy.normalizeUrl(url)else null
    /** Returns true only once; the visible visit keeps its own state until it is left. */
    fun claimIntroduction():Boolean {
        if(prefs.getBoolean("intro-seen",false))return false
        check(prefs.edit().putBoolean("intro-seen",true).commit())
        return true
    }
    fun quoteStart(day:LocalDate):LocalDate {
        if(!prefs.contains("quote-start-day"))check(prefs.edit().putLong("quote-start-day",day.toEpochDay()).commit())
        return runCatching{LocalDate.ofEpochDay(prefs.getLong("quote-start-day",day.toEpochDay()))}.getOrDefault(day)
    }
}
