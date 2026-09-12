package tw.techtarian.browser
import org.junit.Assert.*
import org.junit.Test
class BookmarksTest {
    private val html="""<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p><DT><H3>閱讀</H3><DL><p><DT><H3>科技</H3><DL><p><DT><A HREF="https://example.com/?a=1&amp;b=2">A &amp; B</A></DL><p><DT><A HREF="https://example.org/">Other</A><DT><A HREF="javascript:alert(1)">Unsafe</A></DL><p></DL>"""
    @Test fun importFolders(){val rows=BookmarkFormat.parseHtml(html,100);assertEquals(2,rows.size);val a=rows.find{it.title=="A & B"}!!;assertEquals("閱讀 / 科技",a.folder);assertEquals("https://example.com/?a=1&b=2",a.url);assertEquals("閱讀",rows.find{it.title=="Other"}!!.folder)}
    @Test fun roundtrip(){val source=BookmarkFormat.parseHtml(html,100);assertEquals(source,BookmarkFormat.parseHtml(BookmarkFormat.html(source),100))}
    @Test fun duplicates(){val rows=BookmarkFormat.parseHtml(html,100);assertEquals(rows,BookmarkFormat.merge(rows,rows,rows))}
    @Test fun concurrent(){val a=BookmarkFormat.parseHtml(html,100);val b=a.first().copy(title="Changed",updated=101);val deleted=a.last().copy(deleted=true,updated=102);val result=BookmarkFormat.merge(a,listOf(b,deleted));assertTrue(result.contains(b));assertTrue(result.contains(deleted));assertEquals(result,BookmarkFormat.merge(listOf(b,deleted),a))}
    @Test fun deletion(){val e=BookmarkFormat.parseHtml(html,100).first();val d=e.copy(deleted=true,updated=101);assertEquals(listOf(d),BookmarkFormat.merge(listOf(d),listOf(e)));assertEquals(listOf(d),BookmarkFormat.merge(listOf(e),listOf(d)))}
    @Test fun tie(){val e=BookmarkFormat.parseHtml(html,100).first();assertTrue(BookmarkFormat.merge(listOf(e),listOf(e.copy(deleted=true))).single().deleted)}
    @Test fun snapshot(){val rows=BookmarkFormat.parseHtml(html,100);assertEquals(rows,BookmarkFormat.readSnapshot(BookmarkFormat.snapshot(rows)))}
    @Test(expected=IllegalArgumentException::class) fun otherApp(){BookmarkFormat.readSnapshot("""{"schema":1,"app":"chengjing-notes","bookmarks":[]}""")}
}
