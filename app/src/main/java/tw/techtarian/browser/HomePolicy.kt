package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** No search-engine fallback: a homepage must be an explicit, safe web destination. */
internal object HomePolicy {
    fun normalizeUrl(input:String):String? {
        val value=input.trim()
        if(value.isBlank()||value.length>8192||value.any{it.isWhitespace()||it.code<32||it.code==127})return null
        val explicit=value.startsWith("https://",true)||value.startsWith("http://",true)
        // Permit a host:port but never javascript:, file:, intent: or other schemes.
        if(!explicit&&Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)&&
            !Regex("^[^/:]+:[0-9]+(?:/.*)?$").matches(value))return null
        val parsed=(if(explicit)value else "https://$value").toHttpUrlOrNull()?:return null
        if(parsed.username.isNotEmpty()||parsed.password.isNotEmpty())return null
        if(!explicit&&'.' !in parsed.host&&parsed.host!="localhost"&&':' !in parsed.host)return null
        return parsed.toString()
    }
    fun quoteIndex(start:LocalDate,day:LocalDate,count:Int):Int {
        require(count>0)
        return Math.floorMod(ChronoUnit.DAYS.between(start,day),count.toLong()).toInt()
    }
}
