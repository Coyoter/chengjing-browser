package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HomePolicyTest {
    @Test fun defaultsToHttpsButDoesNotTreatSearchTermsAsUrls(){
        assertEquals("https://techtarian.com/",HomePolicy.normalizeUrl(" techtarian.com "))
        assertEquals("http://localhost:8080/page",HomePolicy.normalizeUrl("http://localhost:8080/page"))
        assertNull(HomePolicy.normalizeUrl("hello world"));assertNull(HomePolicy.normalizeUrl("hello"))
    }
    @Test fun preservesExplicitWebQueriesAndAnchors(){
        assertEquals("https://example.com/?q=one#two",HomePolicy.normalizeUrl("https://example.com/?q=one#two"))
    }
    @Test fun rejectsUnsafeSchemesCredentialsAndControlCharacters(){
        listOf("javascript:alert(1)","file:///secret","intent://x","content://secret","data:text/html,a","https://user:pass@example.com/","https://example.com/\nnew","about:blank","").forEach{
            assertNull(it,HomePolicy.normalizeUrl(it))
        }
    }
    @Test fun canUseExplicitPortAndInternationalDomain(){
        assertEquals("https://example.com:8080/",HomePolicy.normalizeUrl("example.com:8080"))
        assertNotNull(HomePolicy.normalizeUrl("https://例子.台灣/"))
    }
    @Test fun dailyCycleUsesAllEightyNineSlotsInOrder(){
        val start=LocalDate.of(2026,9,18)
        assertEquals((0..88).toList(),(0..88).map{HomePolicy.quoteIndex(start,start.plusDays(it.toLong()),89)})
        assertEquals(0,HomePolicy.quoteIndex(start,start.plusDays(89),89))
        assertEquals(1,HomePolicy.quoteIndex(start,start.plusDays(90),89))
    }
    @Test fun sameDayDoesNotAdvanceOnRepeatedVisits(){
        val date=LocalDate.of(2026,9,18)
        repeat(100){assertEquals(0,HomePolicy.quoteIndex(date,date,89))}
    }
    @Test fun calendarDaysDoNotDependOnDstDayLength(){
        val zone=ZoneId.of("America/New_York")
        val start=LocalDate.of(2026,3,8)
        val next=start.plusDays(1)
        assertEquals(23,java.time.Duration.between(start.atStartOfDay(zone),next.atStartOfDay(zone)).toHours().toInt())
        assertEquals(1,HomePolicy.quoteIndex(start,next,89))
    }
    @Test fun backwardsClockStillHasAnInBoundsDeterministicQuote(){
        val start=LocalDate.of(2026,9,18)
        assertEquals(88,HomePolicy.quoteIndex(start,start.minusDays(1),89))
        assertEquals(0,HomePolicy.quoteIndex(start,start.minusDays(89),89))
    }
    @Test fun skippedDaysAdvanceAndYearBoundaryIsNormal(){
        val start=LocalDate.of(2026,12,31)
        assertEquals(3,HomePolicy.quoteIndex(start,LocalDate.of(2027,1,3),89))
    }
}
