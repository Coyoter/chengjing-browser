package tw.techtarian.browser
import org.junit.Test
import org.junit.Assert.*
class DeveloperProposalsTest {
    private val current=SiteRules("example.com",css="h1 { color:green; }",edits=listOf(PageEdit(id="old",selector="#card",html="<p>原本的內容</p>")))
    @Test fun legacyRulesStillDeserialize(){val old=org.json.JSONObject("""{"domain":"example.com","css":"p {}","rules":[]}""");val read=SiteRules.from(old);assertTrue(read.edits.isEmpty());assertEquals("",read.html)}
    @Test fun editsRoundTripAndAiPreservesUnchangedCode(){val restored=SiteRules.from(current.json());assertEquals(current,restored);val p=DeveloperProposals.parse("""{"explanation":"新增提醒","edits":[{"selector":"#card","html":"<p>休息一下</p>"}]}""",current,null);assertEquals(current.css,p.result.css);assertEquals(2,p.result.edits.size);assertTrue(p.changes.isNotEmpty())}
    @Test fun scopedAiRejectsBroaderEdit(){assertThrows(IllegalArgumentException::class.java){DeveloperProposals.parse("""{"edits":[{"selector":"#other","css":"color:red"}]}""",current,"#card")};assertThrows(IllegalArgumentException::class.java){DeveloperProposals.parse("""{"css":"* { color:red }"}""",current,"#card")}}
    @Test fun restoreOnlyKnownEditsWithinScope(){val p=DeveloperProposals.parse("""{"restoreEditIds":["old"]}""",current,"#card");assertTrue(p.result.edits.isEmpty());assertThrows(IllegalArgumentException::class.java){DeveloperProposals.parse("""{"restoreEditIds":["missing"]}""",current,null)}}
    @Test fun noOpHasNoApplyChanges(){assertTrue(DeveloperProposals.parse("""{"explanation":"需要更多資訊"}""",current,null).changes.isEmpty())}
}
