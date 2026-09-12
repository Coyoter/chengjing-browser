package tw.techtarian.browser
import org.junit.Assert.*
import org.junit.Test
class BrowserDataTest {
    @Test fun domains(){assertEquals("example.co.uk",Domains.scope("https://news.example.co.uk/article"));assertTrue(Domains.matches("news.example.com","example.com"));assertFalse(Domains.matches("badexample.com","example.com"));assertFalse(Domains.matches("example.com.evil.test","example.com"))}
    @Test fun privateDomains(){assertEquals("alice.github.io",Domains.scope("https://alice.github.io/x"));assertNotEquals(Domains.scope("https://alice.github.io"),Domains.scope("https://bob.github.io"))}
    @Test fun addresses(){assertTrue(Domains.address("javascript:alert(1)").startsWith("https://www.google.com/search"));assertTrue(Domains.address("澄境 筆記").contains("search?q="));assertEquals("https://example.com/",Domains.address("example.com"))}
    @Test fun dangerousSelectors(){listOf("body","html", "*", "#ad, body", "#ad { display:none }").forEach{assertNotNull(RuleValidation.selectorError(it))};assertNull(RuleValidation.selectorError("#overlay > .content"))}
    @Test fun boundedAi(){val p=RuleValidation.parseAi("""{"explanation":"縮小範圍","add":[".modal"],"remove":["#overlay"],"unlockScroll":true}""",SiteRules("example.com",listOf(ElementRule("#overlay"))));assertEquals(listOf(".modal"),p.result.rules.map{it.selector});assertTrue(p.result.unlockScroll)}
    @Test(expected=IllegalArgumentException::class) fun noUnknownRule(){RuleValidation.parseAi("""{"remove":[".unknown"]}""",SiteRules("example.com"))}
    @Test(expected=IllegalArgumentException::class) fun noBody(){RuleValidation.parseAi("""{"add":["body"]}""",SiteRules("example.com"))}
}
