package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Whole-site last-write-wins records; empty settings are retained as deletion records. */
data class SiteSettingsRecord(val settings:SiteRules,val modified:Long){
    fun json()=JSONObject().put("modified",modified).put("settings",settings.json())
    fun fingerprint()=MessageDigest.getInstance("SHA-256").digest(settings.json().toString().toByteArray()).joinToString(""){"%02x".format(it)}
}
object SiteSettingsFormat {
    const val TAG="chengjing-browser-site-settings-v1"
    fun merge(vararg snapshots:List<SiteSettingsRecord>):List<SiteSettingsRecord> = snapshots.flatMap{it}.groupBy{it.settings.domain}.map{(_,items)->items.maxWith(compareBy<SiteSettingsRecord>{it.modified}.thenBy{it.fingerprint()})}.sortedBy{it.settings.domain}
    fun write(records:List<SiteSettingsRecord>):String{
        val merged=merge(records);require(merged.size<=1500){"網站設定數量過多"}
        val raw=JSONObject().put("type","site-settings").put("schema",1).put("sites",JSONArray(merged.map{it.json()})).toString()
        require(raw.toByteArray().size<=8_000_000){"網站設定超過 8 MB，請先整理較大的自訂程式碼"}
        return raw
    }
    fun read(raw:String):List<SiteSettingsRecord>{
        require(raw.toByteArray().size<=8_000_000){"網站設定快照過大"}
        val j=JSONObject(raw);require(j.optInt("schema")==1&&j.optString("type")=="site-settings"){"網站設定版本不相容"}
        val list=j.getJSONArray("sites");require(list.length()<=1500){"網站設定數量過多"}
        return merge((0 until list.length()).map{i->
            val record=list.getJSONObject(i);val rules=SiteRules.from(record.getJSONObject("settings"));val time=record.getLong("modified")
            require(time in 0 until Long.MAX_VALUE&&Domains.scope("https://${rules.domain}/")==rules.domain){"網站設定網域或時間不正確"}
            require(rules.rules.size<=100&&rules.edits.size<=100&&rules.css.length<=50000&&rules.js.length<=50000&&rules.html.length<=64000){"網站設定超過限制"}
            require(rules.rules.all{RuleValidation.selectorError(it.selector)==null});rules.edits.forEach{it.validate()}
            SiteSettingsRecord(rules,time)
        })
    }
}
