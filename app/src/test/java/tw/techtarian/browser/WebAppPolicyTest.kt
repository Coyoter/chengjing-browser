package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class WebAppPolicyTest {
    @Test fun installableUrlAcceptsWebAndStripsCredentialsAndRejectsPractice() {
        assertEquals(
            "https://example.com/notes/today?v=1",
            WebAppPolicy.installableUrl("https://user:pass@example.com/notes/today?v=1"),
        )
        assertEquals("http://127.0.0.1:8080/app", WebAppPolicy.installableUrl("http://127.0.0.1:8080/app"))
        assertNull(WebAppPolicy.installableUrl("https://practice.chengjing.invalid/"))
        assertNull(WebAppPolicy.installableUrl("about:blank"))
        assertNull(WebAppPolicy.installableUrl("javascript:alert(1)"))
        assertNull(WebAppPolicy.installableUrl("file:///etc/hosts"))
        assertNull(WebAppPolicy.installableUrl(""))
    }

    @Test fun sanitizeNameRemovesControlsAndFallsBackToHost() {
        assertEquals("My App", WebAppPolicy.sanitizeName("  My\n\tApp  ", "https://example.com/"))
        assertEquals("example.com", WebAppPolicy.sanitizeName("   ", "https://example.com/path"))
        assertEquals(WebAppPolicy.MAX_NAME_LENGTH, WebAppPolicy.sanitizeName("a".repeat(90), "https://example.com").length)
    }

    @Test fun parseThemeColorHandlesHexAndRgbAndRejectsInvalid() {
        assertEquals(0xFF147A64.toInt(), WebAppPolicy.parseThemeColor("#147A64"))
        assertEquals(0xFF112233.toInt(), WebAppPolicy.parseThemeColor("#123"))
        assertEquals(0xFF147A64.toInt(), WebAppPolicy.parseThemeColor("#147A6480"))
        assertEquals(0xFF102030.toInt(), WebAppPolicy.parseThemeColor("rgb(16, 32, 48)"))
        assertEquals(0xFF102030.toInt(), WebAppPolicy.parseThemeColor("rgba(16, 32, 48, 0.5)"))
        assertNull(WebAppPolicy.parseThemeColor("rgb(300, 0, 0)"))
        assertNull(WebAppPolicy.parseThemeColor("not-a-color"))
    }

    @Test fun parsePageMetadataPrefersManifestNameKeepsCurrentUrlAndRanksIcons() {
        val pageInfo = """
            {
              "title": "Document Title",
              "themeColor": "#223344",
              "manifestUrl": "/app.webmanifest",
              "icons": [
                {"href": "/vector.svg", "rel": "icon", "type": "image/svg+xml"},
                {"href": "/small.ico", "rel": "icon", "sizes": "16x16"},
                {"href": "/apple.png", "rel": "apple-touch-icon", "sizes": "180x180"}
              ]
            }
        """.trimIndent()
        val manifest = """
            {
              "short_name": "澄境筆記",
              "name": "澄境筆記網頁版",
              "start_url": "/",
              "display": "minimal-ui",
              "theme_color": "#147A64",
              "icons": [
                {"src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png"},
                {"src": "icons/icon.svg", "sizes": "any", "type": "image/svg+xml"}
              ]
            }
        """.trimIndent()

        val meta = WebAppPolicy.parsePageMetadata(
            pageUrl = "https://notes.example.com/workspace/doc-42",
            pageTitle = "Fallback Title",
            pageInfoJson = pageInfo,
            manifestJson = manifest,
            manifestUrl = "https://notes.example.com/app.webmanifest",
        )

        assertEquals("澄境筆記", meta.name)
        // Always preserves the exact current page URL rather than overriding with start_url.
        assertEquals("https://notes.example.com/workspace/doc-42", meta.url)
        assertEquals(0xFF147A64.toInt(), meta.themeColor)
        assertEquals(WebAppDisplayMode.MINIMAL_UI, meta.suggestedMode)
        assertEquals(
            listOf(
                "https://notes.example.com/icons/icon-512.png",
                "https://notes.example.com/apple.png",
                "https://notes.example.com/small.ico",
            ),
            meta.iconUrls,
        )
    }

    @Test fun scopeCheckDetectsSameDomainSubdomainExternalAndHttpsDowngrade() {
        val app = InstalledWebApp(
            id = "wa_0123456789abcdef",
            name = "Example",
            url = "https://app.example.com/dashboard",
            scope = "example.com",
            mode = WebAppDisplayMode.STANDALONE,
        )
        assertTrue(WebAppPolicy.isInScope(app, "https://app.example.com/settings"))
        assertTrue(WebAppPolicy.isInScope(app, "https://cdn.example.com/view"))
        assertFalse(WebAppPolicy.isInScope(app, "http://app.example.com/dashboard"))
        assertFalse(WebAppPolicy.isInScope(app, "https://evil-example.com/login"))
        assertFalse(WebAppPolicy.isInScope(app, "javascript:void(0)"))
    }

    @Test fun shortcutUriAndStoredListRoundTripRejectsInvalidEntries() {
        val id = WebAppPolicy.idForUrl("https://example.com/page")
        assertTrue(WebAppPolicy.validId(id))
        assertEquals(id, WebAppPolicy.parseShortcutId(WebAppPolicy.shortcutUri(id)))
        assertNull(WebAppPolicy.parseShortcutId("chengjing-webapp://app/../../etc"))
        assertNull(WebAppPolicy.parseShortcutId("https://example.com/wa_12345678"))

        val a = InstalledWebApp(id, "Page A", "https://example.com/page", "example.com", WebAppDisplayMode.STANDALONE, 0xFF147A64.toInt(), "$id.png", 10L, 20L)
        val b = InstalledWebApp("wa_fedcba9876543210", "Page B", "https://other.org/", "other.org", WebAppDisplayMode.SHORTCUT, null, null, 15L, 30L)
        val raw = WebAppPolicy.serializeList(listOf(a, b))
        val parsed = WebAppPolicy.parseStoredList(raw)
        assertEquals(listOf(b, a), parsed)
    }
}
