package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class BrowsingDataPolicyTest {
    private val now=1_800_000_000_000L
    @Test fun allSixRangesHaveExactInclusiveBoundaries(){
        assertEquals(listOf(900_000L,3_600_000L,86_400_000L,604_800_000L,2_419_200_000L,null),BrowsingTimeRange.entries.map{it.durationMillis})
        BrowsingTimeRange.entries.filter{it.durationMillis!=null}.forEach{range->
            val window=range.window(now);val start=now-range.durationMillis!!
            assertFalse(window.contains(start-1));assertTrue(window.contains(start))
            assertTrue(window.contains(now));assertFalse(window.contains(now+1))
            assertFalse(window.contains(0));assertFalse(window.contains(-1))
        }
        assertTrue(BrowsingTimeRange.ALL_TIME.window(now).contains(0))
    }
    @Test fun oldHistoryAndSearchesRemainUndatedUntilAllTimeDeletion(){
        val pages=BrowsingHistoryFormat.pages("""[{"url":"https://old.example/","title":"舊頁面"}]""")
        val searches=BrowsingHistoryFormat.searches("""["舊搜尋"]""")
        assertEquals(0L,pages.single().visitedAt);assertEquals(0L,searches.single().searchedAt)
        val finite=BrowsingTimeRange.LAST_FOUR_WEEKS.window(now)
        assertEquals(pages,pages.filterNot{finite.contains(it.visitedAt)})
        assertEquals(searches,searches.filterNot{finite.contains(it.searchedAt)})
        assertTrue(pages.filterNot{BrowsingTimeRange.ALL_TIME.window(now).contains(it.visitedAt)}.isEmpty())
    }
    @Test fun timestampRoundTripsAndMalformedRowsDoNotEraseValidRows(){
        val pages=listOf(HistoryPage("https://example.org/","閱讀",now))
        val searches=listOf(HistorySearch("閱讀資料",now))
        assertEquals(pages,BrowsingHistoryFormat.pages(BrowsingHistoryFormat.pagesJson(pages)))
        assertEquals(searches,BrowsingHistoryFormat.searches(BrowsingHistoryFormat.searchesJson(searches)))
        assertEquals(1,BrowsingHistoryFormat.pages("""[null,{},42,{"url":"https://valid.example/","title":"ok"}]""").size)
        assertTrue(BrowsingHistoryFormat.pages("corrupt").isEmpty())
    }
}
