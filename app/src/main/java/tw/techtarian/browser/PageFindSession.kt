package tw.techtarian.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** In-memory, document-scoped search. Queries never enter history, preferences or sync. */
internal interface PageFindTarget {
    fun listen(callback:((Int,Int,Boolean)->Unit)?)
    fun find(query:String)
    fun next(forward:Boolean)
    fun clear()
}

internal class PageFindSession {
    var ownerId:Int? by mutableStateOf(null); private set
    var query by mutableStateOf(""); private set
    var count by mutableIntStateOf(0); private set
    var ordinal by mutableIntStateOf(0); private set
    var searching by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    private var target:PageFindTarget?=null
    private var generation=0L
    val canMove:Boolean get()=query.isNotEmpty()&&!searching&&!failed&&count>0
    val status:String get()=when {
        query.isEmpty()->"—"
        searching->"…"
        failed->"無法搜尋"
        else->"$ordinal/$count"
    }
    val description:String get()=when {
        query.isEmpty()->"輸入要找的文字"
        searching->"正在尋找"
        failed->"此頁面暫時無法搜尋"
        count==0->"找不到符合文字"
        else->"第 $ordinal 筆，共 $count 筆"
    }
    fun open(id:Int,newTarget:PageFindTarget){
        close();ownerId=id;target=newTarget
    }
    fun update(value:String){
        val current=target?:return
        query=value.take(4096)
        count=0;ordinal=0;failed=false
        val version=++generation
        searching=query.isNotEmpty()
        try {
            if(query.isEmpty()) {
                current.listen(null)
                current.find("") // Cancel pending native searches before clearing highlights.
                current.clear()
            } else {
                current.listen{index,total,complete->
                    if(generation==version&&target===current&&query.isNotEmpty()&&complete){
                        count=total.coerceAtLeast(0)
                        ordinal=if(count==0)0 else index.coerceIn(0,count-1)+1
                        searching=false
                    }
                }
                // WebView cancels a pending search when a newer findAllAsync is submitted.
                current.find(query)
            }
        }catch(_:Exception){searching=false;failed=true}
    }
    fun move(forward:Boolean):Boolean {
        if(!canMove)return false
        return try {target?.next(forward);true}catch(_:Exception){failed=true;false}
    }
    fun close(){
        val previous=target
        ++generation;target=null;ownerId=null
        query="";count=0;ordinal=0;searching=false;failed=false
        // Invalidate callbacks before interacting with a possibly departing WebView.
        if(previous!=null){
            runCatching{previous.listen(null)}
            runCatching{previous.find("")}
            runCatching{previous.clear()}
        }
    }
}
