package tw.techtarian.browser

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DriveTransportTest{
    private val server=MockWebServer()
    @Before fun start(){server.start()}
    @After fun stop(){server.shutdown()}
    private fun transport(timeout:Long=1000)=DriveTransport(DriveTransport.client().newBuilder().readTimeout(timeout,TimeUnit.MILLISECONDS).build()){0}
    private fun request(method:String="GET")=Request.Builder().url(server.url("/snapshot")).method(method,if(method=="GET")null else "same-snapshot".toRequestBody()).build()
    @Test fun delayedBodyIsRetriedAndReturnsCompleteBytes(){
        server.enqueue(MockResponse().setBody("late").setBodyDelay(350,TimeUnit.MILLISECONDS));server.enqueue(MockResponse().setBody("complete"))
        assertEquals("complete",transport(100).request(request()));assertEquals(2,server.requestCount)
    }
    @Test fun wholeCallTimeoutAlsoRecoversInsteadOfSurfacingTheRawException(){
        server.enqueue(MockResponse().setBody("late").setBodyDelay(350,TimeUnit.MILLISECONDS));server.enqueue(MockResponse().setBody("complete"))
        val transport=DriveTransport(DriveTransport.client().newBuilder().callTimeout(100,TimeUnit.MILLISECONDS).build()){0}
        assertEquals("complete",transport.request(request()));assertEquals(2,server.requestCount)
    }
    @Test fun throttlingAndServerFailuresRetryButAreBounded(){
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}"""))
        server.enqueue(MockResponse().setResponseCode(503));server.enqueue(MockResponse().setResponseCode(429))
        val failure=assertThrows(SyncHttpException::class.java){transport().request(request())}
        assertEquals(429,failure.code);assertEquals(3,server.requestCount)
    }
    @Test fun authorizationAndPermanentErrorsAreNotRetried(){
        for(code in listOf(401,403,404)){
            val before=server.requestCount;server.enqueue(MockResponse().setResponseCode(code))
            assertEquals(code,assertThrows(SyncHttpException::class.java){transport().request(request())}.code)
            assertEquals(before+1,server.requestCount)
        }
    }
    @Test fun newFilePostIsNeverReplayedAfterAnUncertainResult(){
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows(SyncHttpException::class.java){transport().request(request("POST"))}
        assertEquals(1,server.requestCount)
    }
    @Test fun replacementUploadReusesTheExactBody(){
        server.enqueue(MockResponse().setResponseCode(502));server.enqueue(MockResponse().setBody("saved"))
        assertEquals("saved",transport().request(request("PATCH")))
        assertEquals("same-snapshot",server.takeRequest().body.readUtf8());assertEquals("same-snapshot",server.takeRequest().body.readUtf8())
    }
    @Test fun longServerRetryAfterIsDeferredInsteadOfIgnoringItsMinimum(){
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","120"))
        assertThrows(SyncHttpException::class.java){transport().request(request())};assertEquals(1,server.requestCount)
    }
    @Test fun cancellationInterruptsRetryDelayAndMakesNoMoreRequests(){
        server.enqueue(MockResponse().setResponseCode(503))
        val transport=DriveTransport(DriveTransport.client()){30_000};val pool=Executors.newSingleThreadExecutor()
        try{
            val result=pool.submit<String>{transport.request(request())}
            assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));transport.cancel()
            val failure=assertThrows(java.util.concurrent.ExecutionException::class.java){result.get(2,TimeUnit.SECONDS)}
            assertTrue(failure.cause is kotlinx.coroutines.CancellationException);assertEquals(1,server.requestCount)
        }finally{pool.shutdownNow()}
    }
    @Test fun cancellationClosesAnInFlightNetworkRead(){
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val transport=transport();val pool=Executors.newSingleThreadExecutor()
        try{
            val result=pool.submit<String>{transport.request(request())}
            assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));transport.cancel()
            val failure=assertThrows(java.util.concurrent.ExecutionException::class.java){result.get(2,TimeUnit.SECONDS)}
            assertTrue(failure.cause is kotlinx.coroutines.CancellationException);assertEquals(1,server.requestCount)
        }finally{pool.shutdownNow()}
    }
    @Test fun timeoutsHaveHumanCopyAndPermissionsRemainActionable(){
        val timeout=SyncProblem.from(InterruptedIOException("timeout: secret-url"))
        assertFalse(timeout.requiresAction);assertTrue(timeout.title.contains("連線"));assertFalse(timeout.toString().contains("secret-url"))
        assertFalse(SyncProblem.from(IOException("offline")).requiresAction)
        assertTrue(SyncProblem.from(SyncHttpException(401)).requiresAction)
        assertTrue(SyncProblem.from(SyncHttpException(403)).requiresAction)
        assertFalse(SyncProblem.from(SyncHttpException(403,setOf("rateLimitExceeded"))).requiresAction)
        assertFalse(DriveTransport.transient(javax.net.ssl.SSLHandshakeException("bad certificate")))
        val client=DriveTransport.client();assertEquals(30_000,client.readTimeoutMillis);assertEquals(60_000,client.callTimeoutMillis)
    }
}
