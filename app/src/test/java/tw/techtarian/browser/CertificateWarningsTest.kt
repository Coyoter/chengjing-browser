package tw.techtarian.browser
import org.junit.Assert.*
import org.junit.Test
class CertificateWarningsTest {
    @Test fun subresourceIsAWarningForItsPage(){val w=CertificateWarnings();w.record("https://news.example.com/story","https://cdn.example.net/a.png","憑證已過期");assertTrue(w.messageFor("https://news.example.com/story").contains("cdn.example.net"));assertEquals("",w.messageFor("https://unrelated.example.org/"))}
    @Test fun cachedBypassKeepsWarningAcrossReloadAndDirectResourceVisit(){val w=CertificateWarnings();w.record("https://news.example.com/story","https://cdn.example.net/a.png","憑證已過期");assertTrue(w.messageFor("https://news.example.com/next").isNotEmpty());assertTrue(w.messageFor("https://cdn.example.net:8443/").isNotEmpty())}
    @Test fun portAndDomainBoundaries(){val w=CertificateWarnings();w.record("https://site.example.com:8443/","https://cdn.example.net/x","錯誤");assertEquals("",w.messageFor("https://site.example.com/"));assertEquals("",w.messageFor("https://cdn.example.net.evil.test/"));assertEquals("",w.messageFor("http://cdn.example.net/"))}
    @Test fun mainDocumentBeforeNavigationHasAWarning(){val w=CertificateWarnings();w.record("","https://invalid.example.com/","憑證來源不受信任");assertTrue(w.messageFor("https://invalid.example.com/").contains("未開啟網站例外時"))}
    @Test fun knownResourceCarriesWarningToAnotherPage(){val w=CertificateWarnings();w.record("https://a.example/","https://cdn.example/x","憑證已過期");assertEquals("",w.messageFor("https://b.example/"));w.carryKnownResource("https://b.example/","https://cdn.example/y");assertTrue(w.messageFor("https://b.example/").contains("cdn.example"))}
}
