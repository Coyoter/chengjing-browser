package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PageEdit(val id:String=UUID.randomUUID().toString(),val selector:String,val css:String="",val js:String="",val html:String="",val mode:String="append"){
    fun json()=JSONObject().put("id",id).put("selector",selector).put("css",css).put("js",js).put("html",html).put("mode",mode)
    fun validate(){
        require(RuleValidation.selectorError(selector)==null){bt(R.string.msg_4a16359069dd)}
        require(mode in setOf("append","replace")){bt(R.string.msg_792de2c8f51a)}
        require(css.length<=16000&&js.length<=32000&&html.length<=64000){bt(R.string.msg_e714012d82ce)}
        require(!css.contains('{')&&!css.contains('}')){bt(R.string.msg_8579576181cb)}
    }
    companion object{fun from(j:JSONObject)=PageEdit(j.optString("id").ifBlank{UUID.randomUUID().toString()},j.getString("selector"),j.optString("css"),j.optString("js"),j.optString("html"),j.optString("mode","append")).also{it.validate()}}
}

data class DeveloperProposal(val explanation:String,val result:SiteRules,val selectors:List<String>,val changes:List<String>)
object DeveloperProposals {
    fun parse(raw:String,current:SiteRules,selected:String?):DeveloperProposal{
        val j=JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val edits=j.optJSONArray("edits")?:JSONArray();require(edits.length()<=12){bt(R.string.msg_0dcef4dcf0f6)}
        val additions=(0 until edits.length()).map{PageEdit.from(edits.getJSONObject(it)).copy(id=UUID.randomUUID().toString())}
        val removals=j.optJSONArray("hide")?:JSONArray()
        val hide=(0 until removals.length()).map{removals.getString(it)}
        require(hide.size<=12&&hide.all{RuleValidation.selectorError(it)==null}){bt(R.string.msg_3f1c3cd04309)}
        val drop=j.optJSONArray("restoreEditIds")?:JSONArray();val restored=(0 until drop.length()).map{drop.getString(it)}
        require(restored.all{id->current.edits.any{it.id==id}}){bt(R.string.msg_5c5e2b9c6c03)}
        val restore=j.optJSONArray("restoreSelectors")?:JSONArray();val restoredSelectors=(0 until restore.length()).map{restore.getString(it)}
        require(restoredSelectors.all{s->current.rules.any{it.selector==s}}){bt(R.string.msg_5138c822cde5)}
        if(selected!=null){
            require(additions.all{it.selector==selected}&&hide.all{it==selected}&&restoredSelectors.all{it==selected}&&restored.all{id->current.edits.any{it.id==id&&it.selector==selected}}){bt(R.string.msg_63b576365f0f)}
            require(!j.has("css")&&!j.has("js")&&!j.has("html")&&!j.has("unlockScroll")){bt(R.string.msg_4e7c67b2a2aa)}
        }
        val result=current.copy(
            css=if(j.has("css"))j.getString("css")else current.css,
            js=if(j.has("js"))j.getString("js")else current.js,
            html=if(j.has("html"))j.getString("html")else current.html,
            edits=current.edits.filterNot{it.id in restored}+additions,
            rules=(current.rules.filterNot{it.selector in restoredSelectors}+hide.map{ElementRule(it,bt(R.string.msg_bc7f51e47fb7))}).distinctBy{it.selector},
            unlockScroll=if(selected==null)j.optBoolean("unlockScroll",current.unlockScroll)else current.unlockScroll)
        require(result.edits.size<=100&&result.rules.size<=100&&result.css.length<=32000&&result.js.length<=50000&&result.html.length<=64000){bt(R.string.msg_8eb2e7d88b06)}
        val changes=buildList{
            if(result.css!=current.css)add(bt(R.string.msg_8715000a9841))
            if(result.js!=current.js)add(bt(R.string.msg_b350f6663586))
            if(result.html!=current.html)add(bt(R.string.msg_ed10c621ac5e))
            additions.forEach{add("${if(it.mode=="replace")bt(R.string.msg_e0d4485966bd)else bt(R.string.msg_0006d696d8e1)}：${it.selector}")}
            hide.forEach{add(bt(R.string.msg_1b20ab98e475 ,it))}
            if(restored.isNotEmpty()||restoredSelectors.isNotEmpty())add(bt(R.string.msg_c15c3f79a4b3 ,restored.size+restoredSelectors.size))
            if(result.unlockScroll!=current.unlockScroll)add(bt(R.string.msg_58a58537ea7e))
        }
        return DeveloperProposal(j.optString("explanation",bt(R.string.msg_15766e2591bd)).take(6000),result,(additions.map{it.selector}+hide).distinct(),changes)
    }
}
