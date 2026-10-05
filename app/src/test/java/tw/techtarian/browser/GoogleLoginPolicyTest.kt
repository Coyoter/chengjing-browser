package tw.techtarian.browser
import org.junit.Test
import org.junit.Assert.*
class GoogleLoginPolicyTest {
    @Test fun recognizesGoogleLoginWithoutTreatingSpoofedDomainsOrLogoutAsSignIn(){
        assertTrue(GoogleLoginPolicy.isEntry("https://accounts.google.com/o/oauth2/v2/auth?client_id=fixture"))
        assertTrue(GoogleLoginPolicy.isEntry("https://accounts.google.com/ServiceLogin"))
        listOf("http://accounts.google.com/ServiceLogin","https://accounts.google.com.evil.invalid/ServiceLogin","https://accounts.google.com:8443/ServiceLogin","https://evil@accounts.google.com/ServiceLogin","https://accounts.google.com/Logout","https://accounts.google.com/o/oauth2/v2/auth").forEach{assertFalse(it,GoogleLoginPolicy.isEntry(it))}
    }
    @Test fun restartsTheOriginalHttpsWebsiteRatherThanTransferringOAuthStateOrCookies(){
        assertEquals("https://example.com/login",GoogleLoginPolicy.source("https://example.com/login"))
        assertNull(GoogleLoginPolicy.source("https://accounts.google.com/ServiceLogin"))
        assertNull(GoogleLoginPolicy.source("http://example.com/login"))
        assertNull(GoogleLoginPolicy.source("https://user:secret@example.com/"))
    }
}
