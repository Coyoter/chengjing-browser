package tw.techtarian.browser
import org.junit.Test
import org.junit.Assert.*
class AddressHistoryTest {
    @Test fun partialTitleAndMultipleWordsMatch(){val rows=AddressHistory.suggestions("澄境 閱讀",emptyList(),listOf("https://example.com/story" to "澄境瀏覽器閱讀功能","https://example.org/" to "其他文章"));assertEquals(1,rows.size);assertEquals("https://example.com/story",rows[0].input)}
    @Test fun searchTermsAreLocalSuggestionsAndRankExactMatchFirst(){val rows=AddressHistory.suggestions("gemma",listOf("Gemma","Gemma 4 教學"),listOf("https://example.com/gemma" to "Local models"));assertEquals("Gemma",rows.first().input);assertTrue(rows.first().search);assertEquals(3,rows.size)}
    @Test fun capsAndDeduplicatesRecentResults(){assertEquals(6,AddressHistory.suggestions("",(1..20).map{"query $it"},emptyList()).size);assertEquals(1,AddressHistory.suggestions("same",listOf("same","same"),emptyList()).size)}
}
