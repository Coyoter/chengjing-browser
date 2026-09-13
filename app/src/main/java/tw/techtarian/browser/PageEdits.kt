package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PageEdit(val id:String=UUID.randomUUID().toString(),val selector:String,val css:String="",val js:String="",val html:String="",val mode:String="append"){
    fun json()=JSONObject().put("id",id).put("selector",selector).put("css",css).put("js",js).put("html",html).put("mode",mode)
    fun validate(){
        require(RuleValidation.selectorError(selector)==null){"請選取有效元件"}
        require(mode in setOf("append","replace")){"HTML 操作方式不正確"}
        require(css.length<=16000&&js.length<=32000&&html.length<=64000){"單次程式碼過長，請拆成較小的修改"}
        require(!css.contains('{')&&!css.contains('}')){"元件 CSS 請直接填寫屬性，例如 color: #147a64;"}
    }
    companion object{fun from(j:JSONObject)=PageEdit(j.optString("id").ifBlank{UUID.randomUUID().toString()},j.getString("selector"),j.optString("css"),j.optString("js"),j.optString("html"),j.optString("mode","append")).also{it.validate()}}
}

data class DeveloperProposal(val explanation:String,val result:SiteRules,val selectors:List<String>,val changes:List<String>)
object DeveloperProposals {
    fun parse(raw:String,current:SiteRules,selected:String?):DeveloperProposal{
        val j=JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val edits=j.optJSONArray("edits")?:JSONArray();require(edits.length()<=12){"一次建議過多，請縮小問題範圍"}
        val additions=(0 until edits.length()).map{PageEdit.from(edits.getJSONObject(it)).copy(id=UUID.randomUUID().toString())}
        val removals=j.optJSONArray("hide")?:JSONArray()
        val hide=(0 until removals.length()).map{removals.getString(it)}
        require(hide.size<=12&&hide.all{RuleValidation.selectorError(it)==null}){"元件規則不正確"}
        val drop=j.optJSONArray("restoreEditIds")?:JSONArray();val restored=(0 until drop.length()).map{drop.getString(it)}
        require(restored.all{id->current.edits.any{it.id==id}}){"AI 指定不存在的修改"}
        val restore=j.optJSONArray("restoreSelectors")?:JSONArray();val restoredSelectors=(0 until restore.length()).map{restore.getString(it)}
        require(restoredSelectors.all{s->current.rules.any{it.selector==s}}){"AI 指定不存在的元件規則"}
        if(selected!=null){
            require(additions.all{it.selector==selected}&&hide.all{it==selected}&&restoredSelectors.all{it==selected}&&restored.all{id->current.edits.any{it.id==id&&it.selector==selected}}){"元件 AI 的建議超出選取範圍"}
            require(!j.has("css")&&!j.has("js")&&!j.has("html")&&!j.has("unlockScroll")){"元件 AI 不可改動網站層級設定"}
        }
        val result=current.copy(
            css=if(j.has("css"))j.getString("css")else current.css,
            js=if(j.has("js"))j.getString("js")else current.js,
            html=if(j.has("html"))j.getString("html")else current.html,
            edits=current.edits.filterNot{it.id in restored}+additions,
            rules=(current.rules.filterNot{it.selector in restoredSelectors}+hide.map{ElementRule(it,"AI 元件調整")}).distinctBy{it.selector},
            unlockScroll=if(selected==null)j.optBoolean("unlockScroll",current.unlockScroll)else current.unlockScroll)
        require(result.edits.size<=100&&result.rules.size<=100&&result.css.length<=32000&&result.js.length<=50000&&result.html.length<=64000){"建議內容過大，請縮小範圍"}
        val changes=buildList{
            if(result.css!=current.css)add("更新網站 CSS")
            if(result.js!=current.js)add("更新網站 JavaScript")
            if(result.html!=current.html)add("更新網站新增 HTML")
            additions.forEach{add("${if(it.mode=="replace")"編輯"else"新增"}：${it.selector}")}
            hide.forEach{add("移除元件：$it")}
            if(restored.isNotEmpty()||restoredSelectors.isNotEmpty())add("恢復 ${restored.size+restoredSelectors.size} 項修改")
            if(result.unlockScroll!=current.unlockScroll)add("調整頁面捲動設定")
        }
        return DeveloperProposal(j.optString("explanation","請確認下方修改內容").take(6000),result,(additions.map{it.selector}+hide).distinct(),changes)
    }
}
