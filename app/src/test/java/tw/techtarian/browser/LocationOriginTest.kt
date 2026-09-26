package tw.techtarian.browser

import org.junit.Test
import org.junit.Assert.*

class LocationOriginTest{
    @Test fun permissionsUseExactSecureOrigins(){
        assertEquals("https://example.com",LocationOrigin.of("https://EXAMPLE.com:443/path?query=1#fragment"))
        assertEquals("https://example.com:8443",LocationOrigin.of("https://example.com:8443/path"))
        assertNotEquals(LocationOrigin.of("https://example.com"),LocationOrigin.of("https://sub.example.com"))
        assertNull(LocationOrigin.of("http://example.com"));assertNull(LocationOrigin.of("file:///local"))
        assertNull(LocationOrigin.of("https://user:password@example.com"))
        assertEquals("http://127.0.0.1:8000",LocationOrigin.of("http://127.0.0.1:8000/fixture"))
    }
}
