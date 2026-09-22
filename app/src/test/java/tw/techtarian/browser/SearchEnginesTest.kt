package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class SearchEnginesTest {
    @Test fun defaultRemainsGoogleAndEveryBuiltInProducesAnEncodedHttpsUrl(){
        assertEquals("https://www.google.com/search?q=%E6%BE%84%E5%A2%83%20%26%20AI",Domains.address("澄境 & AI"))
        val expected=mapOf(
            "google" to "https://www.google.com/search?q=ChengJing%20browser",
            "bing" to "https://www.bing.com/search?q=ChengJing%20browser",
            "yahoo" to "https://search.yahoo.com/search?p=ChengJing%20browser",
            "baidu" to "https://www.baidu.com/s?wd=ChengJing%20browser",
            "naver" to "https://search.naver.com/search.naver?query=ChengJing%20browser",
            "wiki" to "https://zh.wikipedia.org/w/index.php?search=ChengJing%20browser",
        )
        expected.forEach{(id,url)->assertEquals(url,Domains.resolve("ChengJing browser",SearchSettings(id)).url)}
    }
    @Test fun urlDetectionDoesNotChangeWithTheSelectedEngine(){
        for(id in SearchEngines.ids){
            val settings=SearchSettings(id,"https://find.example/search?q={query}")
            assertEquals(AddressResolution("https://example.com/path",false),Domains.resolve("example.com/path",settings))
            assertEquals(AddressResolution("http://localhost:8080/",false),Domains.resolve("localhost:8080",settings))
            assertEquals(AddressResolution("",false),Domains.resolve("https:// bad",settings))
        }
    }
    @Test fun customTemplateRequiresOneSafePlaceholderAndPreservesItsFixedParameters(){
        val valid="https://find.example/search?language=zh-TW&q={query}&safe=on"
        assertNull(SearchEngines.templateError(valid))
        assertEquals("https://find.example/search?language=zh-TW&q=%E6%BE%84%E5%A2%83%20%26%20AI&safe=on",SearchEngines.searchUrl("澄境 & AI",SearchSettings("custom",valid)))
        listOf("","https://find.example/search?q=test","http://find.example/?q={query}",
            "https://user:pass@find.example/?q={query}","https://find.example/?q={query}#{query}",
            "https://find.example/?a={query}&b={query}","https://{query}.example.com/search",
            "javascript:{query}").forEach{assertNotNull(it,SearchEngines.templateError(it))}
    }
    @Test fun aCorruptCustomPreferenceSafelyFallsBackToGoogle(){
        assertEquals("https://www.google.com/search?q=safe",SearchEngines.searchUrl("safe",SearchSettings("custom","javascript:{query}")))
        assertEquals("Google",SearchEngines.label(SearchSettings("missing")))
    }
    @Test fun builtInSearchUrlsCanStillBeRecognizedForLocalHistory(){
        SearchEngines.builtIns.forEach{assertTrue(SearchEngines.looksLikeSearch(SearchEngines.searchUrl("history",SearchSettings(it.id))))}
        assertFalse(SearchEngines.looksLikeSearch("https://example.com/search?q=history"))
    }
}
