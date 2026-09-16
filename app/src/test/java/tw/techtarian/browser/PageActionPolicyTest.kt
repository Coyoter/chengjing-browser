package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class PageActionPolicyTest {
    @Test fun shareKeepsQueryAndFragment(){
        val url="https://example.com/article?id=42#reading"
        assertEquals(url,PageActionPolicy.shareUrl(url))
    }
    @Test fun embeddedCredentialsAreNotShared(){
        assertEquals("https://example.com/article?q=ok#part",PageActionPolicy.shareUrl("https://reader:password@example.com/article?q=ok#part"))
    }
    @Test fun blankAndNonWebPagesCannotBeShared(){
        listOf(null,""," ","about:blank","javascript:alert(1)","file:///secret","content://private/1","data:text/plain,secret").forEach{assertNull(PageActionPolicy.shareUrl(it))}
    }
    @Test fun rawControlCharactersAreRejected(){
        listOf("https://example.com/\rheader","https://example.com/\nheader","https://example.com/\u0000").forEach{
            assertNull(PageActionPolicy.shareUrl(it));assertNull(PageActionPolicy.downloadUrl(it))
        }
    }
    @Test fun imageDownloadKeepsSignedQueryButRemovesFragment(){
        assertEquals("https://images.example.com/photo.png?signature=abc",PageActionPolicy.downloadUrl("https://images.example.com/photo.png?signature=abc#preview"))
    }
    @Test fun unsupportedImagesAndCredentialUrlsNeverReachDownloadManager(){
        listOf("blob:https://example.com/123","data:image/png;base64,AA==","file:///secret.png","https://user:pass@example.com/image.png").forEach{assertNull(PageActionPolicy.downloadUrl(it))}
    }
    @Test fun nativeContextUrlsResolveAgainstThePage(){
        assertEquals("https://example.com/images/a.png",PageActionPolicy.resolve("../images/a.png","https://example.com/article/1"))
        assertEquals("https://cdn.example.com/a.png",PageActionPolicy.resolve("//cdn.example.com/a.png","https://example.com/"))
        assertNull(PageActionPolicy.resolve("","https://example.com/"))
    }
    @Test fun referrerNeverContainsPrivatePathsQueriesOrCredentials(){
        assertEquals("https://example.com/",PageActionPolicy.referrer("https://user:pass@example.com/private/chapter?secret=1#part","https://cdn.example.com/image.png"))
    }
    @Test fun httpsReferrerIsNotSentToHttp(){
        assertNull(PageActionPolicy.referrer("https://example.com/private","http://cdn.example.com/image.png"))
    }
    @Test fun sameFileNameGetsDistinctSafeDestinations(){
        val a=PageActionPolicy.safeFilename("../image:name.png","first")
        val b=PageActionPolicy.safeFilename("../image:name.png","second")
        assertNotEquals(a,b)
        assertTrue(a.endsWith("-first.png"))
        assertFalse(a.contains('/'));assertFalse(a.contains(':'));assertFalse(a.startsWith('.'))
    }
    @Test fun longUnicodeFilenameHasABoundedUtf8Size(){
        val result=PageActionPolicy.safeFilename("圖片".repeat(200)+".jpg","abcdefgh")
        assertTrue(result.toByteArray(Charsets.UTF_8).size<200)
        assertTrue(result.endsWith("-abcdefgh.jpg"))
    }
    @Test fun emptyFilenameHasReadableFallback(){
        assertEquals("image-abcdefgh",PageActionPolicy.safeFilename("..","abcdefgh"))
    }
}
