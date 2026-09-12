package tw.techtarian.browser
import org.junit.Assert.*
import org.junit.Test
class LibraryDataTest {
    private fun book(i:Int,folder:String)=Bookmark(Bookmark.id("https://example.com/$i",folder),"https://example.com/$i","Book $i",folder,1)
    @Test fun nestedFoldersDoNotFlatten(){val rows=listOf(book(1,"書籤列 / 小說 / 武俠"),book(2,"書籤列 / 小說 / 科幻"),book(3,"行動版書籤"),book(4,""));val t=BookmarkTree(rows);assertEquals(2,t.root.children.size);assertEquals(1,t.root.bookmarks.size);assertEquals(2,t.root.children["書籤列"]!!.total);assertEquals(0,t.node(listOf("書籤列"))!!.bookmarks.size);assertEquals(2,t.node(listOf("書籤列","小說"))!!.children.size);assertEquals(listOf(rows[0]),t.node(listOf("書籤列","小說","武俠"))!!.bookmarks)}
    @Test fun manyBookmarksKeepRootSmall(){val rows=(0 until 2000).map{book(it,"書籤列 / 分類${it%10}")};val t=BookmarkTree(rows);assertEquals(1,t.root.children.size);assertEquals(0,t.root.bookmarks.size);assertEquals(2000,t.root.total);assertEquals(200,t.node(listOf("書籤列","分類0"))!!.bookmarks.size)}
    @Test fun treeDoesNotRewriteBookmarkData(){val rows=listOf(book(1,"資料夾 / 子層"));val original=rows.map{it.json().toString()};BookmarkTree(rows);assertEquals(original,rows.map{it.json().toString()})}
    @Test fun favoriteUpdateKeepsItsIdentityAndCustomName(){val first=Favorite.capture(null,"https://example.com/chapter1","第一章",200.0,.2,1).copy(title="我的小說");val next=Favorite.capture(first,"https://example.com/chapter2","第二章",300.0,.4,2);assertEquals(first.id,next.id);assertEquals("我的小說",next.title);assertEquals("第二章",next.pageTitle);assertEquals(.4,next.progress,0.0)}
    @Test fun defaultFavoriteTitleFollowsChapter(){val first=Favorite.capture(null,"https://example.com/1","第一章",0.0,0.0,1);val next=Favorite.capture(first,"https://example.com/2","第二章",0.0,0.0,2);assertEquals("第二章",next.title)}
    @Test fun favoritePositionIsSanitized(){val f=Favorite.capture(null,"https://example.com/","Title",Double.NaN,Double.POSITIVE_INFINITY);assertEquals(0.0,f.scrollY,0.0);assertEquals(0.0,f.progress,0.0);assertEquals(f,Favorite.from(f.json()))}
}
