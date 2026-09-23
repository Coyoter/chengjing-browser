package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserBackHistoryTest {
    @Test fun firstPageDoesNotReturnToANativeHomepage(){
        assertNull(BrowserBackHistory.previousOffset(listOf("about:blank","https://example.com/article"),1))
        assertNull(BrowserBackHistory.previousOffset(listOf("https://example.com/article"),0))
    }
    @Test fun backSkipsOnlyNativeHomeAndKeepsRealHistory(){
        assertEquals(-2,BrowserBackHistory.previousOffset(listOf("https://example.com/a","about:blank","https://example.com/b"),2))
        assertEquals(-1,BrowserBackHistory.previousOffset(listOf("https://example.com/custom-home","https://example.com/b"),1))
        assertEquals(-1,BrowserBackHistory.previousOffset(listOf("https://example.com/a#one","https://example.com/a#two"),1))
    }
    @Test fun nativeHomeCanReturnToAnEarlierWebpage(){
        assertEquals(-1,BrowserBackHistory.previousOffset(listOf("https://example.com/a","about:blank"),1))
    }
    @Test fun emptyAndOutOfRangeHistoriesHaveNoPreviousPage(){
        assertNull(BrowserBackHistory.previousOffset(emptyList(),-1))
        assertNull(BrowserBackHistory.previousOffset(listOf("about:blank#start",""),1))
        assertNull(BrowserBackHistory.previousOffset(listOf("https://example.com/a"),4))
    }
}
