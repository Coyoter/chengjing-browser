package tw.techtarian.browser

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class PositionOptionsTest{
    @Test fun siteTimeoutAndCacheAgeArePreservedWithinBounds(){
        assertEquals(PositionOptions(true,12000,30000),PositionOptions.from(JSONObject("""{"highAccuracy":true,"timeout":12000,"maximumAge":30000}""")))
        assertEquals(PositionOptions(),PositionOptions.from(JSONObject()))
    }
    @Test fun untrustedInputsCannotCreateUnboundedOneShotGpsRequests(){
        assertEquals(120000L,PositionOptions.from(JSONObject().put("timeout",Long.MAX_VALUE)).timeout)
        val negative=PositionOptions.from(JSONObject("""{"timeout":-1,"maximumAge":-2}"""))
        assertEquals(0L,negative.timeout);assertEquals(0L,negative.maximumAge)
        assertEquals(4_294_967_295L,PositionOptions.from(JSONObject().put("maximumAge",Long.MAX_VALUE)).maximumAge)
    }
}
