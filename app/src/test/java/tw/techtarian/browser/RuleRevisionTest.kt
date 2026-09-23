package tw.techtarian.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuleRevisionTest {
    private val item=PageEdit("saved-id","#card",css="color: green;",js="element.title='保留';",html="<b>舊內容</b>")
    private val sibling=PageEdit("other-id","#other",html="<p>其他內容</p>")
    private val site=SiteRules("example.com",rules=listOf(ElementRule("#ad","隱藏")),css="p {line-height:1.8}",js="window.reader=true",html="<footer>保留</footer>",edits=listOf(item,sibling))
    @Test fun revisionReplacesSameIdInSamePositionAndPreservesOtherRules(){
        val p=RuleRevision.parse("""{"explanation":"調整顏色","css":"color: blue;","html":""}""",site,item.id)
        assertEquals(2,p.result.edits.size);assertEquals(item.copy(css="color: blue;",html=""),p.result.edits[0])
        assertEquals(sibling,p.result.edits[1]);assertEquals(site.css,p.result.css);assertEquals(site.rules,p.result.rules)
        assertEquals(listOf("#card"),p.selectors);assertEquals(2,p.changes.size)
    }
    @Test fun websiteRevisionPreservesEveryElementAndOmittedField(){
        val p=RuleRevision.parse("""{"js":"","html":"<p>更新</p>"}""",site,null)
        assertEquals(site.copy(js="",html="<p>更新</p>"),p.result)
    }
    @Test fun modelCannotChangeTargetOrOtherSettings(){
        for(json in listOf("""{"selector":"#other"}""","""{"edits":[]}""","""{"restoreEditIds":["other-id"]}""","""{"guard":false}""")){
            assertThrows(IllegalArgumentException::class.java){RuleRevision.parse(json,site,item.id)}
        }
        assertThrows(IllegalArgumentException::class.java){RuleRevision.parse("""{"mode":"replace"}""",site,null)}
        assertThrows(IllegalArgumentException::class.java){RuleRevision.parse("""{"css":"body {color:red}"}""",site,item.id)}
    }
    @Test fun noChangeCannotEnableApply(){assertTrue(RuleRevision.parse("""{"explanation":"請補充需求"}""",site,item.id).changes.isEmpty())}
    @Test fun promptContainsFreshSourceOldRuleAndNewRequestWithoutOtherRules(){
        val source=JSONObject().put("html","<section id='card'>網站原文</section>").put("truncated",false)
        val context=RuleRevision.context("改成藍色，保留提示",source.toString(),site,item.id)
        assertEquals(source.getString("html"),context.getJSONObject("originalSource").getString("html"))
        assertEquals(item.json().toString(),context.getJSONObject("existingRule").toString())
        assertEquals("改成藍色，保留提示",context.getString("revisionRequest"));assertFalse(context.toString().contains("other-id"))
    }
    @Test fun boundedSourceIsExplicitlyMarkedTruncated(){
        val source=JSONObject().put("html","a".repeat(30000))
        val remote=RuleRevision.context("修改",source.toString(),site,null).getJSONObject("originalSource")
        assertEquals(24000,remote.getString("html").length);assertTrue(remote.getBoolean("truncated"))
        val local=RuleRevision.context("修改",source.toString(),site,item.id,true).getJSONObject("originalSource")
        assertEquals(6000,local.getString("html").length);assertTrue(local.getBoolean("truncated"))
    }
}
