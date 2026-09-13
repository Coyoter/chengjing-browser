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
    @Test fun editingUrlRetiresTheOldSyncIdentity(){
        val old=Bookmark(Bookmark.id("https://example.com/old","閱讀"),"https://example.com/old","原名稱","閱讀",10)
        val changed=BookmarkFormat.editChanges(old,"新名稱","閱讀"," https://example.com/new?q=1#chapter ",20)
        val saved=BookmarkFormat.readSnapshot(BookmarkFormat.snapshot(BookmarkFormat.merge(listOf(old),changed)))
        assertTrue(saved.single{it.id==old.id}.deleted)
        val current=saved.single{!it.deleted}
        assertEquals("https://example.com/new?q=1#chapter",current.url)
        assertEquals(Bookmark.id(current.url,current.folder),current.id)
        assertEquals(listOf(current),BookmarkFormat.merge(saved,listOf(old)).filterNot{it.deleted})
        assertEquals(current.url,BookmarkFormat.parseHtml(BookmarkFormat.html(saved)).single().url)
    }
    @Test fun editingIntoAnExistingUrlDoesNotDuplicateIt(){
        val old=Bookmark(Bookmark.id("https://example.com/old","閱讀"),"https://example.com/old","原名稱","閱讀",10)
        val existing=old.copy(id=Bookmark.id("https://example.com/new","閱讀"),url="https://example.com/new",updated=11)
        val result=BookmarkFormat.merge(listOf(old,existing),BookmarkFormat.editChanges(old,"合併名稱","閱讀",existing.url,20))
        assertEquals(1,result.count{!it.deleted});assertEquals("合併名稱",result.single{!it.deleted}.title)
    }
    @Test fun editingTitleOnlyKeepsTheOriginalUrlIdentity(){
        val old=Bookmark(Bookmark.id("https://example.com",""),"https://example.com","原名稱","",10)
        val saved=BookmarkFormat.merge(listOf(old),BookmarkFormat.editChanges(old,"新名稱","",old.url,20)).single()
        assertEquals(old.id,saved.id);assertEquals(old.url,saved.url);assertFalse(saved.deleted)
    }
    @Test fun editedUrlsRejectEmptyInvalidAndScriptValues(){
        listOf("","example.com","https://","javascript:alert(1)","file:///sdcard/file","https://example.com/\nnext","https://example.com/"+"a".repeat(16000),"https://example.com/"+"澄".repeat(2000)).forEach{assertTrue(it.take(30),runCatching{BookmarkFormat.editedUrl(it)}.isFailure)}
    }
}
