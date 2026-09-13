package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class CertificateExceptionsTest {
    @Test fun canonicalOriginIgnoresPathCredentialsAndDefaultPort(){
        assertEquals("https://example.com/",CertificateExceptions.site("https://user:password@EXAMPLE.com:443/a?q=1#x"))
        assertEquals(CertificateExceptions.site("https://example.com/a"),CertificateExceptions.site("https://example.com/b"))
    }
    @Test fun onlyExactHttpsHostIsEquivalent(){
        val origin=CertificateExceptions.site("https://example.com/")
        assertEquals(origin,CertificateExceptions.site("https://example.com:8443/"))
        listOf("https://www.example.com/","https://example.com.evil.test/").forEach{assertNotEquals(origin,CertificateExceptions.site(it))}
        listOf("http://example.com/","javascript:alert(1)","about:blank","").forEach{assertNull(CertificateExceptions.site(it))}
    }
    @Test fun subresourceOriginsSurviveCachedWarningTransfer(){
        val warnings=CertificateWarnings()
        warnings.record("https://a.example/","https://cdn.example:8443/pixel","憑證無效")
        warnings.carryKnownResource("https://b.example/","https://cdn.example:8443/other")
        assertEquals(listOf("https://cdn.example/"),warnings.originsFor("https://b.example/next"))
    }
}
