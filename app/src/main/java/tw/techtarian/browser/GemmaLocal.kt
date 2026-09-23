package tw.techtarian.browser

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class GemmaLocal(private val context:Context){
    companion object{
        const val MODEL_BYTES=2588147712L
        const val MODEL_SHA="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val MODEL_URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm"
    }
    private val prefs=context.getSharedPreferences("gemma-local",Context.MODE_PRIVATE)
    private val downloads=context.getSystemService(DownloadManager::class.java)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val lock=Mutex()
    private var poll:Job?=null
    @Volatile private var conversation:Conversation?=null
    val modelFile=File(context.getExternalFilesDir(null)?:context.filesDir,"models/gemma-4-e2b.litertlm")
    val supported=Build.SUPPORTED_ABIS.any{it=="arm64-v8a"||it=="x86_64"}
    var ready by mutableStateOf(modelFile.length()==MODEL_BYTES&&prefs.getString("verified","")==MODEL_SHA);private set
    var downloading by mutableStateOf(false);private set
    var progress by mutableFloatStateOf(0f);private set
    var status by mutableStateOf(if(ready)"Gemma 4 E2B 已就緒"else"尚未下載本機模型");private set
    var error by mutableStateOf("");private set
    init{if(!ready&&prefs.getLong("download",-1)>0)watchDownload()}
    fun startDownload(metered:Boolean){
        if(downloading||ready)return
        runCatching{
            check(supported){"本機模型需要 64 位元裝置"}
            modelFile.parentFile!!.mkdirs()
            check(modelFile.parentFile!!.usableSpace>MODEL_BYTES+300_000_000){"空間不足，請先保留約 3 GB 可用儲存空間"}
            if(modelFile.exists())modelFile.delete()
            val request=DownloadManager.Request(Uri.parse(MODEL_URL)).setTitle("澄境 · Gemma 4 本機模型").setDescription("下載約 2.59 GB；完成後會核對模型完整性")
                .setAllowedOverMetered(metered).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(modelFile))
            val id=downloads.enqueue(request);prefs.edit().putLong("download",id).remove("verified").apply();error="";watchDownload()
        }.onFailure{error=it.localizedMessage.orEmpty()}
    }
    private fun watchDownload(){
        if(poll?.isActive==true)return
        downloading=true
        poll=scope.launch{
            try{
                while(isActive&&!ready){
                    val id=prefs.getLong("download",-1);if(id<0)break
                    val state=withContext(Dispatchers.IO){downloads.query(DownloadManager.Query().setFilterById(id)).use{cursor->
                        if(!cursor.moveToFirst())return@use null
                        Triple(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                    }}?:error("下載工作已不存在，請重新下載")
                    progress=(state.second.toFloat()/MODEL_BYTES).coerceIn(0f,1f)
                    when(state.first){
                        DownloadManager.STATUS_SUCCESSFUL->{status="正在核對模型完整性…";verifyModel();break}
                        DownloadManager.STATUS_FAILED->error("模型下載失敗（${state.third}），請重新下載")
                        DownloadManager.STATUS_PAUSED->status="下載已暫停；請確認 Wi-Fi、網路或儲存空間"
                        else->status="正在下載 Gemma 4 · ${(progress*100).toInt()}%"
                    }
                    delay(1500)
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.localizedMessage.orEmpty();prefs.edit().remove("download").apply();status="尚未完成下載"}
            finally{downloading=false}
        }
    }
    internal suspend fun verifyModel(){
        val valid=withContext(Dispatchers.IO){
            if(modelFile.length()!=MODEL_BYTES)return@withContext false
            val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(1024*1024)
            modelFile.inputStream().use{input->while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
            digest.digest().joinToString(""){"%02x".format(it)}==MODEL_SHA
        }
        check(valid){"模型完整性檢查未通過，請刪除後重新下載"}
        prefs.edit().putString("verified",MODEL_SHA).remove("download").apply();ready=true;progress=1f;status="Gemma 4 E2B 已就緒";error=""
    }
    fun removeModel(){
        poll?.cancel();val id=prefs.getLong("download",-1);if(id>0)downloads.remove(id)
        modelFile.delete();prefs.edit().clear().apply();ready=false;downloading=false;progress=0f;status="尚未下載本機模型";error=""
    }
    fun cancelInference(){conversation?.cancelProcess()}
    suspend fun develop(problem:String,structure:String,current:SiteRules,selected:String?,revision:Boolean=false,editId:String?=null):DeveloperProposal=withContext(Dispatchers.IO){
        lock.withLock{
            check(supported&&ready&&modelFile.length()==MODEL_BYTES){"請先下載 Gemma 4 本機模型"}
            val memory=ActivityManager.MemoryInfo();context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            check(memory.availMem>=1_800_000_000L){"目前可用記憶體不足，請先關閉部分分頁或其他 App，再使用本機模型。"}
            val prompt=(if(revision)RuleRevision.context(problem,structure,current,editId,true)else DeveloperPrompt.context(problem,structure,current,selected,true)).toString()
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            val config=EngineConfig(modelPath=modelFile.absolutePath,backend=Backend.CPU(threadCount=4),maxNumTokens=8192,cacheDir=File(context.cacheDir,"gemma").apply{mkdirs()}.absolutePath)
            Engine(config).use{engine->
                engine.initialize();currentCoroutineContext().ensureActive()
                engine.createConversation(ConversationConfig(systemInstruction=Contents.of(if(revision)RuleRevision.system else if(selected==null)DeveloperPrompt.system else DeveloperPrompt.elementSystem),samplerConfig=SamplerConfig(topK=20,topP=0.9,temperature=0.2),maxOutputToken=1800,thinkingConfig=ThinkingConfig(enableThinking=false))).use{chat->
                    conversation=chat
                    try{
                        val text=StringBuilder()
                        chat.sendMessageAsync(prompt).collect{message->currentCoroutineContext().ensureActive();text.append(message.toString());check(text.length<=48000){"模型回覆過長，請縮小問題範圍"}}
                        if(revision)RuleRevision.parse(text.toString(),current,editId)else if(selected==null)DeveloperProposals.parse(text.toString(),current,null)else DeveloperPrompt.localElementProposal(text.toString(),current,selected)
                    }finally{chat.cancelProcess();conversation=null}
                }
            }
        }
    }
    fun close(){cancelInference();scope.cancel()}
}
