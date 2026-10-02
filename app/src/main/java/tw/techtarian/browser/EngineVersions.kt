package tw.techtarian.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.WebSettings
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class InstalledEngine(val version:String,val provider:String,val packageName:String,val platform:String)
data class StableEngine(val version:String,val checkedAt:Long)
object ChromiumVersions {
    const val endpoint="https://versionhistory.googleapis.com/v1/chrome/platforms/webview/channels/stable/versions?pageSize=1&orderBy=version%20desc"
    private val pattern=Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$")
    fun compare(left:String,right:String):Int?{
        fun parts(value:String):List<Long>?{
            if(!pattern.matches(value))return null
            return value.split('.').map{it.toLongOrNull()?:return null}
        }
        val a=parts(left)?:return null;val b=parts(right)?:return null
        return a.zip(b).firstOrNull{it.first!=it.second}?.let{it.first.compareTo(it.second)}?:0
    }
    fun parse(raw:String,platform:String="webview"):String{
        require(platform in setOf("webview","android"))
        val rows=JSONObject(raw).getJSONArray("versions")
        require(rows.length()>0){bt(R.string.msg_8e57db190d94)}
        val row=rows.getJSONObject(0);val version=row.getString("version")
        require(row.getString("name")=="chrome/platforms/$platform/channels/stable/versions/$version"&&compare(version,version)==0){bt(R.string.msg_df93d17ea7a2)}
        return version
    }
    fun installed(context:Context):InstalledEngine{
        val info=WebViewCompat.getCurrentWebViewPackage(context)
        val packageName=info?.packageName.orEmpty()
        val googleProvider=packageName.startsWith("com.google.android.webview")||packageName in setOf("com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary")
        val packageVersion=info?.versionName.orEmpty().takeIf{compare(it,it)==0}
        // Default UA belongs to the real engine, not the user-configurable per-WebView UA.
        val engine=if(googleProvider&&packageVersion!=null)packageVersion else Regex("Chrome/([0-9.]+)").find(WebSettings.getDefaultUserAgent(context))?.groupValues?.get(1).orEmpty()
        val provider=runCatching{info?.applicationInfo?.loadLabel(context.packageManager)?.toString()}.getOrNull()?:packageName.ifBlank{bt(R.string.msg_b4a2d538390f)}
        return InstalledEngine(engine.ifBlank{bt(R.string.msg_b7d90d419331)},provider,packageName,if(packageName in setOf("com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary"))"android"else"webview")
    }
}
class EngineVersionRepository(context:Context,private val platform:String="webview"){
    init{require(platform in setOf("webview","android"))}
    private val prefs=context.getSharedPreferences("engine-version-check",Context.MODE_PRIVATE)
    private val client=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).build()
    fun cached():StableEngine?{
        val version=prefs.getString("stable:$platform",null)?:return null
        val time=prefs.getLong("checked-at:$platform",0)
        return if(ChromiumVersions.compare(version,version)==0&&time>0)StableEngine(version,time)else null
    }
    suspend fun refresh():StableEngine=withContext(Dispatchers.IO){
        val version=client.newCall(Request.Builder().url(ChromiumVersions.endpoint.replace("/webview/","/$platform/")).build()).execute().use{response->
            check(response.isSuccessful){bt(R.string.msg_737d4fde158c)}
            val raw=response.body?.byteStream()?.readBounded(65537)?:error(bt(R.string.msg_6090d27dccad))
            require(raw.size<=65536){bt(R.string.msg_3bbd188b2227)}
            ChromiumVersions.parse(String(raw,Charsets.UTF_8),platform)
        }
        val result=StableEngine(version,System.currentTimeMillis())
        check(prefs.edit().putString("stable:$platform",version).putLong("checked-at:$platform",result.checkedAt).commit()){ bt(R.string.msg_f3ac0161d2ad) }
        result
    }
}
internal fun openEngineUpdate(context:Context,engine:InstalledEngine){
    if(engine.packageName.isBlank())return
    val market=Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+Uri.encode(engine.packageName)))
    runCatching{context.startActivity(market)}.onFailure{
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+engine.packageName)))
    }
}
