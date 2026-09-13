package tw.techtarian.browser
import org.junit.Test
import org.junit.Assert.*
class SiteSettingsFormatTest {
    private val old=SiteSettingsRecord(SiteRules("example.com",rules=listOf(ElementRule("#panel")),css="p{color:green}",edits=listOf(PageEdit(id="one",selector="#card",html="<p>提醒</p>"))),100)
    @Test fun allCustomizationsRoundTrip(){assertEquals(listOf(old),SiteSettingsFormat.read(SiteSettingsFormat.write(listOf(old))))}
    @Test fun newerEmptySettingsPreventResurrection(){val cleared=SiteSettingsRecord(SiteRules("example.com"),200);assertEquals(listOf(cleared),SiteSettingsFormat.merge(listOf(old),listOf(cleared),listOf(old)))}
    @Test fun independentWebsitesArePreserved(){val other=SiteSettingsRecord(SiteRules("example.org",js="document.title='read'"),90);assertEquals(2,SiteSettingsFormat.merge(listOf(old),listOf(other)).size)}
    @Test fun concurrentSameTimeChangesConverge(){val peer=old.copy(settings=old.settings.copy(css="p{color:blue}"));assertEquals(SiteSettingsFormat.merge(listOf(old),listOf(peer)),SiteSettingsFormat.merge(listOf(peer),listOf(old)))}
    @Test fun rejectsBookmarkOrFutureSchema(){assertThrows(IllegalArgumentException::class.java){SiteSettingsFormat.read("""{"schema":1,"type":"bookmarks","sites":[]}""")};assertThrows(IllegalArgumentException::class.java){SiteSettingsFormat.read("""{"schema":2,"type":"site-settings","sites":[]}""")}}
    @Test fun malformedSnapshotCannotSupplyBroadRemoval(){val raw=SiteSettingsFormat.write(listOf(old)).replace("#panel","body");assertThrows(IllegalArgumentException::class.java){SiteSettingsFormat.read(raw)}}
}
