package tw.techtarian.browser

import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Per-device snapshots avoid two phones overwriting the same Drive file. Deletions are retained. */
class BookmarkDrive(private val token:String){
    private val client=OkHttpClient.Builder().callTimeout(40,TimeUnit.SECONDS).build()
    private val appTag="chengjing-browser-bookmarks-v1"
    fun cancel(){client.dispatcher.cancelAll()}
    private fun request(url:String,method:String="GET",body:RequestBody?=null):String{
        client.newCall(Request.Builder().url(url).header("Authorization","Bearer $token").method(method,body).build()).execute().use{r->
            check(r.isSuccessful){when(r.code){
                401,403->throw SyncHttpException(r.code,"Google 授權已過期或尚未完成，請重新連結")
                429->throw SyncHttpException(r.code,"Google Drive 請求受速率限制")
                else->throw SyncHttpException(r.code,"同步未完成（HTTP ${r.code}），本機資料已保留")
            }}
            val content=r.body?:error("Google Drive 沒有回傳資料")
            require(content.contentLength()<=12_000_000){"雲端資料超過大小限制"}
            val bytes=content.byteStream().readBounded(12_000_001);require(bytes.size<=12_000_000){"雲端資料過大"};return String(bytes,Charsets.UTF_8)
        }
    }
    fun account():Pair<String,String>{
        val j=JSONObject(request("https://www.googleapis.com/drive/v3/about?fields=user(permissionId,displayName,emailAddress)")).getJSONObject("user")
        return j.getString("permissionId") to j.optString("emailAddress",j.optString("displayName","Google 帳戶"))
    }
    fun files(tag:String=appTag):List<JSONObject>{
        val result=mutableListOf<JSONObject>();var cursor=""
        do{
            val url="https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder().addQueryParameter("spaces","appDataFolder").addQueryParameter("q","trashed=false and appProperties has { key='app' and value='$tag' }").addQueryParameter("fields","files(id,appProperties),nextPageToken").addQueryParameter("pageSize","100").apply{if(cursor.isNotEmpty())addQueryParameter("pageToken",cursor)}.build().toString()
            val j=JSONObject(request(url));val rows=j.getJSONArray("files");for(i in 0 until rows.length())result.add(rows.getJSONObject(i));cursor=j.optString("nextPageToken")
            require(result.size<=200){"同步裝置快照過多，請聯絡支援"}
        }while(cursor.isNotEmpty())
        return result
    }
    fun read(id:String)=BookmarkFormat.readSnapshot(request("https://www.googleapis.com/drive/v3/files/$id?alt=media"))
    fun write(device:String,id:String?,rows:List<Bookmark>):String = writeRaw(device,id,BookmarkFormat.snapshot(rows))
    fun readRaw(id:String)=request("https://www.googleapis.com/drive/v3/files/$id?alt=media")
    fun writeRaw(device:String,id:String?,raw:String,tag:String=appTag):String{
        val response=if(id!=null)request("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media","PATCH",raw.toRequestBody("application/json; charset=UTF-8".toMediaType()))
        else{
            val boundary="cj_browser_bookmarks_v1"
            val metadata=JSONObject().put("name","ChengJing-Browser-$tag-$device.json").put("parents",JSONArray().put("appDataFolder")).put("appProperties",JSONObject().put("app",tag).put("device",device))
            val multipart="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$raw\r\n--$boundary--\r\n"
            request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id","POST",multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
        }
        return JSONObject(response).getString("id")
    }
}
class BookmarkSync(private val activity:MainActivity,private val store:BookmarkStore){
    var details by mutableStateOf("")
    var status by mutableStateOf(if(store.connected)"等待同步"else"尚未連結 Google")
    var busy by mutableStateOf(false)
    var connected by mutableStateOf(store.connected)
    var launchConsent:((IntentSenderRequest)->Unit)?=null
    private val tasks=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var pending:Job?=null
    private var epoch=0
    private val mutex=Mutex()
    private var authorizing=false
    private var consentEpoch:Int?=null
    private var currentDrive:BookmarkDrive?=null
    private val favorites get()=activity.controller.favorites
    companion object{const val SCOPE="https://www.googleapis.com/auth/drive.appdata"}
    init{
        store.onChange={changed()}
        favorites.onChange={changed()}
        activity.controller.store.onSiteChange={if(activity.controller.store.syncSiteSettings)changed()}
    }
    fun changed(){activity.controller.revision++;if(connected){pending?.cancel();pending=tasks.launch{delay(1400);authorize(false)}}}
    fun resume(){if(connected&&!busy)authorize(false)}
    private fun apiStatusText(code:Int):String=when(code){
        12501->"使用者在授權畫面取消（12501）"
        CommonStatusCodes.CANCELED->"使用者在授權畫面取消（12501）"
        CommonStatusCodes.SIGN_IN_REQUIRED->"需要重新登入 Google 帳戶"
        CommonStatusCodes.NETWORK_ERROR->"網路暫時中斷，請稍後重試"
        CommonStatusCodes.DEVELOPER_ERROR->"Google 登入設定與本機版本不一致"
        10->"Google 驗證流程設定有問題（10）"
        7->"網路連線不穩定或 Google Play 服務未就緒（7）"
        else->"授權流程失敗（錯誤碼 $code）"
    }
    private fun fail(msg:String,code:Int?=null,detail:String?=null,lock:Boolean=false){
        if(code!=null&&detail!=null)details="code=$code, $detail"
        else details=detail.orEmpty()
        status=if(lock) "同步被中斷" else msg
    }
    fun authorize(interactive:Boolean=true){
        if(authorizing||busy||consentEpoch!=null)return
        val version=epoch;authorizing=true;busy=true;status="正在連結 Google…"
        val request=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request).addOnSuccessListener{result->
            if(version!=epoch)return@addOnSuccessListener
            authorizing=false
            if(result.hasResolution()){
                busy=false
                if(interactive){consentEpoch=version;launchConsent?.invoke(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())}
                else status="需要重新確認 Google 授權，請按立即同步"
            }else accept(result,version)
        }.addOnFailureListener{error->
            if(version!=epoch)return@addOnFailureListener
            busy=false;authorizing=false
            if(error is ApiException){
                val message=apiStatusText(error.statusCode)
                fail(message,error.statusCode,error.toString(),lock=false)
            }else{
                fail("Google 連線未完成，請稍後重試",detail=error.localizedMessage)
            }
        }
    }
    fun consent(data:android.content.Intent?){
        val version=consentEpoch?:return
        consentEpoch=null
        if(version!=epoch)return
        if(data==null){busy=false;fail("授權回傳資料遺失；請重新授權");return}
        runCatching{Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)}.onSuccess{accept(it,version)}.onFailure{
            if(it is ApiException){
                fail(apiStatusText(it.statusCode),it.statusCode,it.toString())
            }else{
                fail("已取消連結；本機資料保持不變",detail=it.localizedMessage)
            }
        }
    }
    private fun accept(result:AuthorizationResult,version:Int){
        val token=result.accessToken
        if(token.isNullOrBlank()){busy=false;status="沒有取得 Google 授權，請再試一次";return}
        busy=true;status="正在同步書籤…"
        tasks.launch{
            mutex.withLock{
                try{
                    val drive=BookmarkDrive(token)
                    currentDrive=drive
                    val (id,label)=withContext(Dispatchers.IO){drive.account()}
                    check(version==epoch){"連線已取消"}
                    check(store.accountId.isEmpty()||store.accountId==id){"此手機的資料已綁定另一個 Google 帳戶；請使用原本帳戶，避免混入其他人的資料"}
                    val files=withContext(Dispatchers.IO){drive.files()}
                    val remote=withContext(Dispatchers.IO){BookmarkFormat.merge(*files.map{drive.read(it.getString("id"))}.toTypedArray())}
                    check(version==epoch){"連線已取消"}
                    // Bind before merging: an upload failure must never let another account inherit remote data.
                    store.accountId=id;store.accountLabel=label
                    // Re-read local records after network awaits: edits made during sync are preserved.
                    store.mergeRemote(remote)
                    val upload=store.all()
                    val own=files.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                    val fileId=withContext(Dispatchers.IO){drive.write(store.deviceId,own,upload)}
                    check(version==epoch){"連線已取消"}
                    // Read back the write before reporting success.
                    val verified=withContext(Dispatchers.IO){drive.read(fileId)}
                    check(BookmarkFormat.snapshot(verified)==BookmarkFormat.snapshot(upload)){"雲端寫入回讀不一致，請重試"}
                    check(version==epoch){"連線已取消"}

                    // Favorites include the current URL, custom title, scroll position, and deletion state.
                    // Use a separate namespace so older bookmark-only clients cannot erase them.
                    status="正在同步收藏與閱讀進度…"
                    val favoriteFiles=withContext(Dispatchers.IO){drive.files(FavoriteFormat.TAG)}
                    val favoriteRemote=withContext(Dispatchers.IO){FavoriteFormat.merge(*favoriteFiles.map{FavoriteFormat.read(drive.readRaw(it.getString("id")))}.toTypedArray())}
                    check(version==epoch){"連線已取消"}
                    if(favorites.mergeRemote(favoriteRemote))activity.controller.revision++
                    val favoriteUpload=favorites.records()
                    val favoriteRaw=FavoriteFormat.snapshot(favoriteUpload)
                    val favoriteOwn=favoriteFiles.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                    val favoriteFileId=withContext(Dispatchers.IO){drive.writeRaw(store.deviceId,favoriteOwn,favoriteRaw,FavoriteFormat.TAG)}
                    check(version==epoch){"連線已取消"}
                    val favoriteVerified=withContext(Dispatchers.IO){FavoriteFormat.read(drive.readRaw(favoriteFileId))}
                    check(version==epoch){"連線已取消"}
                    check(favoriteVerified==favoriteUpload){"收藏與閱讀進度寫入回讀不一致，請重試"}

                    val browserStore=activity.controller.store
                    var siteCount:Int?=null
                    var sitesChangedWhileUploading=false
                    if(browserStore.syncSiteSettings){
                        status="正在同步天眼網站設定…"
                        val siteFiles=withContext(Dispatchers.IO){drive.files(SiteSettingsFormat.TAG)}
                        val siteRemote=withContext(Dispatchers.IO){SiteSettingsFormat.merge(*siteFiles.map{SiteSettingsFormat.read(drive.readRaw(it.getString("id")))}.toTypedArray())}
                        check(version==epoch){"連線已取消"}
                        val changed=browserStore.mergeSiteRecords(siteRemote)
                        if(changed.isNotEmpty())activity.controller.refreshScripts(emptyList(),applyToPage=false)
                        val siteUpload=SiteSettingsFormat.write(browserStore.siteRecords())
                        val siteOwn=siteFiles.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                        val siteFileId=withContext(Dispatchers.IO){drive.writeRaw(store.deviceId,siteOwn,siteUpload,SiteSettingsFormat.TAG)}
                        check(version==epoch){"連線已取消"}
                        val siteVerified=withContext(Dispatchers.IO){SiteSettingsFormat.write(SiteSettingsFormat.read(drive.readRaw(siteFileId)))}
                        check(version==epoch){"連線已取消"}
                        check(siteVerified==siteUpload){"天眼設定寫入回讀不一致，請重試"}
                        sitesChangedWhileUploading=SiteSettingsFormat.write(browserStore.siteRecords())!=siteUpload
                        siteCount=browserStore.all().count{it.rules.isNotEmpty()||it.edits.isNotEmpty()||it.css.isNotBlank()||it.js.isNotBlank()||it.html.isNotBlank()||it.guard||it.unlockScroll}
                    }
                    store.accountId=id;store.accountLabel=label;store.connected=true;connected=true;store.lastSync=System.currentTimeMillis()
                    status="已同步 ${store.visible().size} 個書籤、${favorites.all().size} 個收藏與閱讀進度"+(siteCount?.let{"、$it 個網站設定；重新載入頁面後套用"}?:"");activity.controller.revision++
                    details=""
                    // A user may save/delete a favorite while any network request is in flight.
                    // Do not replace those local edits with the older verified upload.
                    if(sitesChangedWhileUploading||favorites.records()!=favoriteUpload||BookmarkFormat.snapshot(store.all())!=BookmarkFormat.snapshot(upload))changed()
                }catch(e:Exception){if(version==epoch){
                    if(e is SyncHttpException)fail(e.userMessage,e.code, "HTTP ${e.code}: ${e.userMessage}")
                    else if(e is ApiException)fail(apiStatusText(e.statusCode),e.statusCode,e.toString())
                    else fail(e.localizedMessage?:"同步未完成，本機資料已保留",detail=e.toString())
                }}
                finally{if(version==epoch){busy=false;currentDrive=null}}
            }
        }
    }
    fun disconnect(){epoch++;consentEpoch=null;currentDrive?.cancel();currentDrive=null;pending?.cancel();store.connected=false;connected=false;busy=false;authorizing=false;status="已停止同步，本機與雲端書籤、收藏均保留"}
    fun destroy(){epoch++;currentDrive?.cancel();tasks.cancel();store.onChange=null;favorites.onChange=null;activity.controller.store.onSiteChange=null}
}

private class SyncHttpException(val code:Int,val userMessage:String):IllegalStateException("HTTP $code: $userMessage")
