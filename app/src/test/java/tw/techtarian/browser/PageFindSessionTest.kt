package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class PageFindSessionTest {
    private class Target:PageFindTarget {
        var callback:((Int,Int,Boolean)->Unit)?=null
        val queries=mutableListOf<String>()
        val moves=mutableListOf<Boolean>()
        var clears=0
        override fun listen(callback:((Int,Int,Boolean)->Unit)?){this.callback=callback}
        override fun find(query:String){queries.add(query)}
        override fun next(forward:Boolean){moves.add(forward)}
        override fun clear(){clears++}
    }
    @Test fun reportsFinalCountsAndUsesOneBasedPosition(){
        val target=Target();val find=PageFindSession();find.open(1,target);find.update("文字")
        target.callback!!(0,1,false)
        assertTrue(find.searching);assertFalse(find.canMove)
        target.callback!!(1,3,true)
        assertEquals("2/3",find.status);assertEquals("第 2 筆，共 3 筆",find.description);assertTrue(find.canMove)
        assertTrue(find.move(false));assertTrue(find.move(true));assertEquals(listOf(false,true),target.moves)
    }
    @Test fun noMatchesDisableNavigation(){
        val target=Target();val find=PageFindSession();find.open(1,target);find.update("not-found")
        target.callback!!(-1,0,true)
        assertEquals("0/0",find.status);assertEquals("找不到符合文字",find.description);assertFalse(find.move(true))
    }
    @Test fun clearingQueryCancelsAndRemovesHighlightWithoutSearchingBlank(){
        val target=Target();val find=PageFindSession();find.open(1,target);find.update("x")
        val stale=target.callback!!
        find.update("");stale(0,99,true)
        assertEquals("",target.queries.last());assertEquals(1,target.clears);assertNull(target.callback)
        assertEquals("—",find.status);assertFalse(find.canMove)
    }
    @Test fun rapidReplacementIgnoresEarlierCallback(){
        val target=Target();val find=PageFindSession();find.open(1,target);find.update("x")
        val old=target.callback!!;find.update("new");old(0,99,true)
        assertTrue(find.searching);assertEquals(0,find.count)
        target.callback!!(0,2,true);assertEquals("1/2",find.status)
    }
    @Test fun closingDetachesCallbacksAndForgetsQuery(){
        val target=Target();val find=PageFindSession();find.open(1,target);find.update("private query")
        val old=target.callback!!;find.close();old(2,3,true)
        assertNull(find.ownerId);assertEquals("",find.query);assertEquals(0,find.count);assertNull(target.callback)
        assertEquals(1,target.clears);assertFalse(find.move(false))
    }
    @Test fun anotherTabStartsEmptyAndCannotReceiveOldResults(){
        val first=Target();val second=Target();val find=PageFindSession()
        find.open(1,first);find.update("x");val old=first.callback!!
        find.open(2,second);old(0,5,true)
        assertEquals(2,find.ownerId);assertEquals("",find.query);assertEquals(0,find.count);assertEquals(1,first.clears)
    }
    @Test fun nativeFailureIsVisibleAndDoesNotLeaveSpinner(){
        val find=PageFindSession()
        find.open(1,object:PageFindTarget {
            override fun listen(callback:((Int,Int,Boolean)->Unit)?){ }
            override fun find(query:String){error("renderer unavailable")}
            override fun next(forward:Boolean){ }
            override fun clear(){ }
        })
        find.update("x");assertTrue(find.failed);assertEquals("無法搜尋",find.status);assertFalse(find.searching)
        find.close();assertNull(find.ownerId)
    }
    @Test fun preservesUnicodePunctuationAndSpacesAsPageText(){
        val target=Target();val find=PageFindSession();find.open(1,target)
        val value="繁體 café 😀 a.b [x] 空 白"
        find.update(value);assertEquals(value,target.queries.last())
    }
}
