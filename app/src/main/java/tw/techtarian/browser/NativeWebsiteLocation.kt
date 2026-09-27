package tw.techtarian.browser

import androidx.lifecycle.Lifecycle
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import org.json.JSONObject
import org.json.JSONTokener

/** Each reply capability and request is bound to the verified top-level document. */
internal class NativeWebsiteLocation(private val activity:MainActivity,private val permissions:WebsiteLocation){
    private val c get()=activity.controller
    private val source=activity.assets.open("geolocation.js").bufferedReader().use{it.readText()}
    private var foreground=activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private class Query(val id:Int,val watch:Boolean,val options:PositionOptions,val reply:JavaScriptReplyProxy){
        var approved=false
        var request:NativePositionRequest?=null
    }
    private class Page(val tab:BrowserTab,val script:ScriptHandler){
        var doc=""
        var generation=0L
        var authorizing=false
        val queries=linkedMapOf<Int,Query>()
    }
    private val pages=mutableMapOf<Int,Page>()
    val activeProviders:Set<String> get()=pages.values.flatMap{p->p.queries.values.flatMap{it.request?.activeProviders.orEmpty()}}.toSet()
    var registrationCount=0;private set
    fun attached(id:Int)=id in pages
    fun attach(tab:BrowserTab){
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)||!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))return
        WebViewCompat.addWebMessageListener(tab.web,"ChengJingLocation",setOf("*")){_,message,origin,mainFrame,reply->
            if(message.type!=WebMessageCompat.TYPE_STRING)return@addWebMessageListener
            val canonical=LocationOrigin.of(origin.toString())
            if(!mainFrame||canonical==null||tab !in c.tabs)return@addWebMessageListener
            val raw=message.data?:return@addWebMessageListener
            if(raw.length>2048)return@addWebMessageListener
            val json=runCatching{JSONObject(raw)}.getOrNull()?:return@addWebMessageListener
            val doc=json.optString("doc")
            if(!Regex("[0-9a-f]{32}").matches(doc))return@addWebMessageListener
            // Read an immutable access descriptor installed before site JS. This also
            // rejects stale same-origin messages and an explicit Permissions-Policy denial.
            tab.web.evaluateJavascript("window.__chengjingLocationAccess"){proof->
                val access=runCatching{JSONObject(JSONTokener(proof).nextValue() as String)}.getOrNull()
                // getUrl() can be about:blank for app-loaded HTML with a real base origin.
                // The framework's sourceOrigin plus this current-document proof are authoritative.
                if(access?.optString("doc")!=doc||!access.optBoolean("allowed")||!permissions.canUse(tab,canonical)){
                    replyError(reply,doc,json.optInt("id"),1,"此頁面未獲准使用定位。",true);return@evaluateJavascript
                }
                receive(tab,canonical,doc,access.optBoolean("visible"),json,reply)
            }
        }
        val script=WebViewCompat.addDocumentStartJavaScript(tab.web,source,setOf("*"))
        pages[tab.id]=Page(tab,script)
    }
    private fun receive(tab:BrowserTab,origin:String,doc:String,visible:Boolean,json:JSONObject,reply:JavaScriptReplyProxy){
        val page=pages[tab.id]?:return
        if(page.doc!=doc||page.generation!=tab.navigationGeneration){cancelFor(tab.id);permissions.cancelPermissionFor(tab.id);page.doc=doc;page.generation=tab.navigationGeneration}
        val id=json.optInt("id")
        when(json.optString("op")){
            "pause"->{pauseFor(tab.id);return}
            "resume"->{if(foreground&&visible)page.queries.values.toList().forEach{begin(page,it)};return}
            "clear"->{remove(page,id);if(page.queries.isEmpty())permissions.cancelPermissionFor(tab.id);return}
            "get","watch"->{
                if(id<=0)return
                if(!foreground||!visible){replyError(reply,doc,id,2,"目前頁面不在前景。",false);return}
                page.queries[id]?.let{if(it.approved)begin(page,it);return}
                if(page.queries.size>=8){replyError(reply,doc,id,2,"定位請求過多，請稍後再試。",true);return}
                page.queries[id]=Query(id,json.optString("op")=="watch",PositionOptions.from(json),reply)
                if(page.authorizing)return
                page.authorizing=true;permissions.status="等待網站與手機定位授權"
                val generation=page.generation
                permissions.authorizeNative(tab,origin){_,allowed,_->
                    if(page.doc!=doc||page.generation!=generation)return@authorizeNative
                    page.authorizing=false
                    for(query in page.queries.values.toList()){
                        if(!allowed){
                            val code=if(permissions.locationEnabled())1 else 2
                            error(page,query,code,if(code==1)"尚未允許這個網站使用手機位置。"else"手機定位服務尚未開啟。",true)
                        }else{query.approved=true;begin(page,query)}
                    }
                }
            }
        }
    }
    private fun valid(page:Page)=pages[page.tab.id]===page&&page.generation==page.tab.navigationGeneration&&
        permissions.canUse(page.tab,LocationOrigin.of(page.tab.url).orEmpty())
    private fun begin(page:Page,query:Query){
        if(!foreground||!query.approved||query.request!=null||!valid(page))return
        if(!permissions.hasPermission()||permissions.choice(page.tab)!=LocationChoice.ALLOW){error(page,query,1,"手機或網站定位授權已撤回。",true);return}
        if(!permissions.locationEnabled()){error(page,query,2,"手機定位服務尚未開啟。",true);return}
        lateinit var request:NativePositionRequest
        request=NativePositionRequest(activity,query.options,query.watch,
            onStarted={providers->
                registrationCount+=providers.size
                permissions.status="已向手機發出定位請求，正在等候位置"
            },onPosition={position->
                if(foreground&&valid(page)&&page.queries[query.id]===query&&permissions.hasPermission()){
                    permissions.status="已取得位置，已提供給目前網站"
                    reply(query.reply,JSONObject().put("doc",page.doc).put("id",query.id).put("type","position").put("position",position))
                }else if(page.queries[query.id]===query){
                    remove(page,query.id)
                }
                if(!query.watch&&page.queries[query.id]===query)remove(page,query.id)
            },onError={code,message,terminal->
                if(page.queries[query.id]===query){
                    if(valid(page))error(page,query,code,message,terminal)else remove(page,query.id)
                }
            })
        query.request=request;request.start()
    }
    private fun reply(proxy:JavaScriptReplyProxy,value:JSONObject){runCatching{proxy.postMessage(value.toString())}}
    private fun replyError(proxy:JavaScriptReplyProxy,doc:String,id:Int,code:Int,message:String,terminal:Boolean){
        reply(proxy,JSONObject().put("doc",doc).put("id",id).put("type","error").put("code",code).put("message",message).put("terminal",terminal))
    }
    private fun error(page:Page,query:Query,code:Int,message:String,terminal:Boolean){
        permissions.status=message
        replyError(query.reply,page.doc,query.id,code,message,terminal)
        if(terminal||!query.watch)remove(page,query.id)
    }
    private fun remove(page:Page,id:Int){page.queries.remove(id)?.request?.close()}
    fun cancelFor(id:Int){
        val page=pages[id]?:return
        page.queries.values.forEach{it.request?.close()};page.queries.clear();page.doc="";page.authorizing=false
    }
    fun detach(id:Int){cancelFor(id);pages.remove(id)?.let{it.script.remove();WebViewCompat.removeWebMessageListener(it.tab.web,"ChengJingLocation")}}
    fun pauseFor(id:Int){
        val page=pages[id]?:return
        for(query in page.queries.values.toList()){
            query.request?.close();query.request=null
        }
    }
    fun resumeFor(id:Int){
        val page=pages[id]?:return
        if(page.queries.isEmpty()||!foreground||!valid(page))return
        page.tab.web.evaluateJavascript("window.__chengjingLocationAccess"){proof->
            val access=runCatching{JSONObject(JSONTokener(proof).nextValue() as String)}.getOrNull()
            if(foreground&&valid(page)&&access?.optString("doc")==page.doc&&access.optBoolean("allowed")&&access.optBoolean("visible")){
                page.queries.values.toList().forEach{begin(page,it)}
            }
        }
    }
    fun pause(){foreground=false;pages.keys.toList().forEach{pauseFor(it)}}
    fun resume(){foreground=true;resumeFor(c.activeId)}
    fun close(){pages.keys.toList().forEach{detach(it)}}
}
