package tw.techtarian.browser
import org.junit.Assert.*
import org.junit.Test
class BrowserUserAgentTest {
    private val base="Mozilla/5.0 (Linux; Android 16; Pixel Build/AB; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/151.0.7922.199 Mobile Safari/537.36"
    @Test fun mobileUsesChromeIdentityAndRealVersion(){val ua=BrowserUserAgent.mobile(base);assertFalse(ua.contains("; wv"));assertFalse(ua.contains("Version/4.0"));assertTrue(ua.contains("Chrome/151.0.7922.199"));assertTrue(ua.contains("Android 16"));assertTrue(ua.contains("Mobile"))}
    @Test fun desktopHasValidDesktopPlatform(){val ua=BrowserUserAgent.desktop(base);assertTrue(ua.contains("(X11; Linux x86_64)"));assertFalse(ua.contains("Android"));assertFalse(ua.contains("Mobile"));assertTrue(ua.contains("Chrome/151.0.7922.199"))}
}
