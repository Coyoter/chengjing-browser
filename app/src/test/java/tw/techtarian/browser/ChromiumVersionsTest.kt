package tw.techtarian.browser

import org.junit.Test
import org.junit.Assert.*

class ChromiumVersionsTest {
    @Test fun comparesNumericVersionParts(){
        assertTrue(ChromiumVersions.compare("99.0.1.9","154.0.1.2")!!<0)
        assertTrue(ChromiumVersions.compare("154.0.8037.22","154.0.8037.9")!!>0)
        assertEquals(0,ChromiumVersions.compare("154.0.8037.22","154.0.8037.22"))
        assertNull(ChromiumVersions.compare("未知","154.0.8037.22"))
    }
    @Test fun acceptsOnlyTheRequestedOfficialStableChannel(){
        val json="""{"versions":[{"name":"chrome/platforms/webview/channels/stable/versions/154.0.8037.22","version":"154.0.8037.22"}]}"""
        assertEquals("154.0.8037.22",ChromiumVersions.parse(json))
        assertTrue(runCatching{ChromiumVersions.parse(json.replace("stable","beta"))}.isFailure)
        assertTrue(runCatching{ChromiumVersions.parse(json,"android")}.isFailure)
        assertTrue(runCatching{ChromiumVersions.parse("""{"versions":[]}""")}.isFailure)
    }
}
