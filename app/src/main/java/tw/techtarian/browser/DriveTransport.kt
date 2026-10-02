package tw.techtarian.browser

import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.random.Random

/** Only repeat reads and replacement uploads. A new-file POST may already have committed. */
internal class DriveTransport(
    private val client:OkHttpClient=client(),
    private val backoffMillis:(Int)->Long={attempt->(1000L shl attempt)+Random.nextLong(251)}
){
    private val stopped=CountDownLatch(1)
    private val active=AtomicReference<Call?>()
    fun cancel(){stopped.countDown();active.get()?.cancel()}
    private fun ensureActive(){if(stopped.count==0L)throw CancellationException("Drive sync cancelled")}
    fun request(request:Request):String{
        val replayable=request.method in setOf("GET","PATCH")
        repeat(3){attempt->
            ensureActive()
            // Reads/replacements may recover across IPv6/IPv4 routes. Creation must not replay.
            val call=(if(replayable)client else client.newBuilder().retryOnConnectionFailure(false).build()).newCall(request);active.set(call)
            try{
                ensureActive()
                return call.execute().use{response->
                    if(!response.isSuccessful){
                        val errorBody=response.body?.byteStream()?.readBounded(16_384)?.toString(Charsets.UTF_8).orEmpty()
                        val reasons=runCatching{
                            val rows=JSONObject(errorBody).optJSONObject("error")?.optJSONArray("errors")
                            (0 until (rows?.length()?:0)).mapNotNull{rows?.optJSONObject(it)?.optString("reason")}.toSet()
                        }.getOrDefault(emptySet())
                        throw SyncHttpException(response.code,reasons,retryAfter(response.header("Retry-After")))
                    }
                    val body=response.body?:throw IOException("Empty Drive response")
                    require(body.contentLength()<=12_000_000){"Cloud snapshot exceeds limit"}
                    val bytes=body.byteStream().readBounded(12_000_001)
                    require(bytes.size<=12_000_000){"Cloud snapshot exceeds limit"}
                    String(bytes,Charsets.UTF_8)
                }
            }catch(error:IOException){
                ensureActive()
                val retryAfter=(error as? SyncHttpException)?.retryAfterMillis?:0L
                if(!replayable||attempt==2||!transient(error)||retryAfter>30_000)throw error
                // Server delays are a minimum. Long delays are deferred to the next sync.
                if(stopped.await(maxOf(backoffMillis(attempt),retryAfter),TimeUnit.MILLISECONDS))ensureActive()
            }finally{active.compareAndSet(call,null)}
        }
        error("Unreachable")
    }
    companion object{
        private val sharedClient by lazy{OkHttpClient.Builder()
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
            .writeTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).build()}
        fun client()=sharedClient
        fun transient(error:IOException):Boolean=when(error){
            is SyncHttpException->error.retryable
            is SSLException,is ProtocolException->false
            else->true
        }
        private fun retryAfter(value:String?):Long{
            if(value==null)return 0
            value.toLongOrNull()?.let{return it.coerceIn(0,86_400)*1000}
            return runCatching{(ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()).coerceAtLeast(0)}.getOrDefault(0)
        }
    }
}

internal class SyncHttpException(val code:Int,val reasons:Set<String> = emptySet(),val retryAfterMillis:Long=0):IOException("Drive HTTP $code"){
    val retryable get()=code in setOf(408,429,500,502,503,504)||
        (code==403&&reasons.any{it in setOf("rateLimitExceeded","userRateLimitExceeded")})
}

internal class SyncAccountMismatch:IllegalStateException()

/** User copy never includes exception strings, URLs, account identifiers, or API response bodies. */
internal data class SyncProblem(internal val titleCaption:BrowserCaption,internal val messageCaption:BrowserCaption,val requiresAction:Boolean){
    val title:String get()=titleCaption.text()
    val message:String get()=messageCaption.text()
    companion object{
        private val KEPT get()=bcaption(R.string.msg_e7b33daf9a2d)
        fun from(error:Exception):SyncProblem=when{
            error is SyncHttpException&&error.retryable->SyncProblem(bcaption(R.string.msg_0d3a30703357),bcaption(R.string.msg_61b008bafedd ,KEPT),false)
            error is SyncHttpException&&error.code==401->SyncProblem(bcaption(R.string.msg_9fe553752239),bcaption(R.string.msg_2a19048f4610),true)
            error is SyncHttpException&&error.code==403->SyncProblem(bcaption(R.string.msg_b212a1bec5a1),bcaption(R.string.msg_bdd0b148c46c),true)
            error is SyncAccountMismatch->SyncProblem(bcaption(R.string.msg_0108ca4bc750),bcaption(R.string.msg_5b29df305534),true)
            error is SSLException->SyncProblem(bcaption(R.string.msg_e8cda0bcb8d9),bcaption(R.string.msg_84e4b5bb6ec9),true)
            error is InterruptedIOException->SyncProblem(bcaption(R.string.msg_e813605409d0),KEPT,false)
            error is IOException&&error !is SyncHttpException&&error !is ProtocolException->SyncProblem(bcaption(R.string.msg_19e90c6c1d3f),KEPT,false)
            else->SyncProblem(bcaption(R.string.msg_328257927000),bcaption(R.string.msg_f0ef4b5b4105),true)
        }
    }
}
