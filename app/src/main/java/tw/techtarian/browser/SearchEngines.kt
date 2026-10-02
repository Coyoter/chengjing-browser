package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

internal data class SearchEngine(
    val id:String,
    private val labelSource:String,
    private val descriptionSource:String,
    val template:String,
){
    val label:String get()=if(id=="baidu")bt(R.string.msg_455ece81de56)else labelSource
    val description:String get()=if(id=="wiki")bt(R.string.msg_af8a52509166)else descriptionSource
}

data class SearchSettings(
    val engineId:String="google",
    val customTemplate:String="",
)

data class AddressResolution(val url:String,val search:Boolean)

internal object SearchEngines {
    const val placeholder="{query}"
    val builtIns=listOf(
        SearchEngine("google","Google","google.com","https://www.google.com/search?q={query}"),
        SearchEngine("bing","Bing","bing.com","https://www.bing.com/search?q={query}"),
        SearchEngine("yahoo","Yahoo","search.yahoo.com","https://search.yahoo.com/search?p={query}"),
        SearchEngine("baidu","Baidu","baidu.com","https://www.baidu.com/s?wd={query}"),
        SearchEngine("naver","Naver","search.naver.com","https://search.naver.com/search.naver?query={query}"),
        SearchEngine("wiki","Wiki","Chinese Wikipedia","https://zh.wikipedia.org/w/index.php?search={query}"),
    )
    val ids=builtIns.map{it.id}.toSet()+"custom"
    fun engine(id:String)=builtIns.firstOrNull{it.id==id}
    fun label(settings:SearchSettings)=if(settings.engineId=="custom")bt(R.string.msg_1fe9883907ba) else engine(settings.engineId)?.label?:"Google"
    fun description(settings:SearchSettings)=if(settings.engineId=="custom")customHost(settings.customTemplate)?:bt(R.string.msg_db79062a760b) else engine(settings.engineId)?.description?:"google.com"
    fun templateError(raw:String):String? {
        val template=raw.trim()
        if(template.isEmpty())return bt(R.string.msg_4d729b911aeb)
        if(template.length>2048)return bt(R.string.msg_e2d127e64801)
        if(template.any{it=='\r'||it=='\n'||it=='\u0000'})return bt(R.string.msg_a30e3ed7b9b0)
        if(template.windowed(placeholder.length).count{it==placeholder}!=1)return bt(R.string.msg_32c6fb7d1791)
        val marker=template.indexOf(placeholder)
        val afterScheme=template.indexOf("://").takeIf{it>=0}?.plus(3)?:0
        val authorityEnd=listOf(template.indexOf('/',afterScheme),template.indexOf('?',afterScheme)).filter{it>=0}.minOrNull()?:template.length
        if(marker<authorityEnd)return bt(R.string.msg_cbb4e7972e49)
        val test=template.replace(placeholder,"chengjing-search")
        val url=test.toHttpUrlOrNull()?:return bt(R.string.msg_2928c35bd1f9)
        if(url.scheme!="https")return bt(R.string.msg_00f486ce580a)
        if(url.username.isNotEmpty()||url.password.isNotEmpty())return bt(R.string.msg_5a56b71bec4e)
        if(url.fragment!=null)return bt(R.string.msg_b7fdf7ea812f)
        if(url.host.isBlank())return bt(R.string.msg_a9d18f49f4b4)
        return null
    }
    fun normalizeTemplate(raw:String):String {
        val value=raw.trim()
        require(templateError(value)==null){templateError(value).orEmpty()}
        return value
    }
    fun customHost(raw:String)=raw.trim().replace(placeholder,"chengjing-search").toHttpUrlOrNull()?.host
    fun searchUrl(query:String,settings:SearchSettings):String {
        // Percent encoding works in both a URL path and a query parameter; '+' is
        // query-form specific and would become a literal plus in a path template.
        val encoded=URLEncoder.encode(query,Charsets.UTF_8.name()).replace("+","%20")
        val template=when(settings.engineId){
            "custom"->settings.customTemplate.takeIf{templateError(it)==null}?:builtIns.first().template
            else->engine(settings.engineId)?.template?:builtIns.first().template
        }
        return template.replace(placeholder,encoded)
    }
    fun looksLikeSearch(url:String):Boolean=builtIns.any{engine->
        val (prefix,suffix)=engine.template.split(placeholder,limit=2)
        url.startsWith(prefix)&&url.endsWith(suffix)
    }
}
