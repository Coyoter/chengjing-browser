package tw.techtarian.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

internal data class SearchEngine(
    val id:String,
    val label:String,
    val description:String,
    val template:String,
)

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
        SearchEngine("baidu","百度","baidu.com","https://www.baidu.com/s?wd={query}"),
        SearchEngine("naver","Naver","search.naver.com","https://search.naver.com/search.naver?query={query}"),
        SearchEngine("wiki","Wiki","中文維基百科","https://zh.wikipedia.org/w/index.php?search={query}"),
    )
    val ids=builtIns.map{it.id}.toSet()+"custom"
    fun engine(id:String)=builtIns.firstOrNull{it.id==id}
    fun label(settings:SearchSettings)=if(settings.engineId=="custom")"自訂" else engine(settings.engineId)?.label?:"Google"
    fun description(settings:SearchSettings)=if(settings.engineId=="custom")customHost(settings.customTemplate)?:"尚未設定網址範本" else engine(settings.engineId)?.description?:"google.com"
    fun templateError(raw:String):String? {
        val template=raw.trim()
        if(template.isEmpty())return "請輸入搜尋網址範本"
        if(template.length>2048)return "網址範本不可超過 2,048 個字元"
        if(template.any{it=='\r'||it=='\n'||it=='\u0000'})return "網址範本必須是完整的單行網址"
        if(template.windowed(placeholder.length).count{it==placeholder}!=1)return "網址範本必須包含一次 {query}"
        val marker=template.indexOf(placeholder)
        val afterScheme=template.indexOf("://").takeIf{it>=0}?.plus(3)?:0
        val authorityEnd=listOf(template.indexOf('/',afterScheme),template.indexOf('?',afterScheme)).filter{it>=0}.minOrNull()?:template.length
        if(marker<authorityEnd)return "{query} 必須放在網址路徑或查詢參數中"
        val test=template.replace(placeholder,"chengjing-search")
        val url=test.toHttpUrlOrNull()?:return "請輸入完整的 HTTPS 網址"
        if(url.scheme!="https")return "為保護搜尋內容，只能使用 HTTPS"
        if(url.username.isNotEmpty()||url.password.isNotEmpty())return "網址不可包含帳號或密碼"
        if(url.fragment!=null)return "網址範本不可包含 # 錨點"
        if(url.host.isBlank())return "網址需要有效的主機名稱"
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
