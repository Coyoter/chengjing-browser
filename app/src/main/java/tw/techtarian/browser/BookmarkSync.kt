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

class BookmarkSync internal constructor(private val activity:MainActivity,private val store:BookmarkStore,private val driveFactory:(String)->BookmarkDrive={BookmarkDrive(it)}){
    var details by mutableStateOf("")
    var status by mutableStateOf(if(store.connected)"等待同步"else"尚未連結 Google")
    var busy by mutableStateOf(false)
    var requiresAttention by mutableStateOf(false)
    private var lastStart:Long?=null
    private var lastSuccess:Long?=null
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
    fun resume(){
        val now=android.os.SystemClock.elapsedRealtime()
        if(connected&&!busy&&(lastStart==null||now-lastStart!!>=30_000)&&
            (lastSuccess==null||now-lastSuccess!!>=60_000))authorize(false)
    }
    private fun apiProblem(code:Int):SyncProblem=when(code){
        12501,CommonStatusCodes.CANCELED->SyncProblem("已取消 Google 授權","本機資料保持不變，之後可再按「立即同步」。",false)
        CommonStatusCodes.NETWORK_ERROR->SyncProblem("等待網路連線","本機資料已保留，連線恢復後可按「立即同步」。",false)
        CommonStatusCodes.SIGN_IN_REQUIRED->SyncProblem("需要重新登入 Google","請按「立即同步」確認 Google 帳戶。",true)
        CommonStatusCodes.DEVELOPER_ERROR->SyncProblem("Google 登入設定不相容","請更新至最新版；若仍無法連結，請聯絡支援。",true)
        else->SyncProblem("Google 授權未完成","請按「立即同步」再試一次。本機資料已保留。",true)
    }
    private fun fail(problem:SyncProblem){status=problem.title;details=problem.message;requiresAttention=problem.requiresAction}
    private fun clearProblem(){details="";requiresAttention=false}
    fun authorize(interactive:Boolean=true){
        if(authorizing||busy||consentEpoch!=null)return
        val version=epoch;authorizing=true;busy=true;lastStart=android.os.SystemClock.elapsedRealtime();clearProblem();status="正在連結 Google…"
        val request=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request).addOnSuccessListener{result->
            if(version!=epoch)return@addOnSuccessListener
            authorizing=false
            if(result.hasResolution()){
                busy=false
                if(interactive){consentEpoch=version;launchConsent?.invoke(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())}
                else fail(apiProblem(CommonStatusCodes.SIGN_IN_REQUIRED))
            }else accept(result,version)
        }.addOnFailureListener{error->
            if(version!=epoch)return@addOnFailureListener
            busy=false;authorizing=false
            if(error is ApiException){
                fail(apiProblem(error.statusCode))
            }else{
                fail(SyncProblem.from(error))
            }
        }
    }
    fun consent(data:android.content.Intent?){
        val version=consentEpoch?:return
        consentEpoch=null
        if(version!=epoch)return
        if(data==null){busy=false;fail(apiProblem(CommonStatusCodes.CANCELED));return}
        runCatching{Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)}.onSuccess{accept(it,version)}.onFailure{
            busy=false
            fail(if(it is ApiException)apiProblem(it.statusCode)else SyncProblem("Google 授權未完成","本機資料保持不變，請再試一次。",true))
        }
    }
    private fun accept(result:AuthorizationResult,version:Int){
        val token=result.accessToken
        if(token.isNullOrBlank()){busy=false;fail(apiProblem(CommonStatusCodes.SIGN_IN_REQUIRED));return}
        syncAuthorized(token,version)
    }
    internal fun syncAuthorized(token:String,version:Int=epoch){
        busy=true;clearProblem();status="正在同步書籤…"
        tasks.launch{
            mutex.withLock{
                try{
                    val drive=driveFactory(token)
                    currentDrive=drive
                    val (id,label)=withContext(Dispatchers.IO){drive.account()}
                    check(version==epoch){"連線已取消"}
                    if(store.accountId.isNotEmpty()&&store.accountId!=id)throw SyncAccountMismatch()
                    val files=withContext(Dispatchers.IO){drive.files()}
                    val snapshots=withContext(Dispatchers.IO){files.associate{it.getString("id") to drive.read(it.getString("id"))}}
                    val remote=BookmarkFormat.merge(*snapshots.values.toTypedArray())
                    check(version==epoch){"連線已取消"}
                    // Bind before merging: an upload failure must never let another account inherit remote data.
                    store.accountId=id;store.accountLabel=label
                    // Re-read local records after network awaits: edits made during sync are preserved.
                    store.mergeRemote(remote)
                    val upload=store.all()
                    val own=files.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                    if(own==null||BookmarkFormat.snapshot(snapshots.getValue(own))!=BookmarkFormat.snapshot(upload)){
                        val fileId=withContext(Dispatchers.IO){drive.write(store.deviceId,own,upload)}
                        check(version==epoch){"連線已取消"}
                        val verified=withContext(Dispatchers.IO){drive.read(fileId)}
                        check(BookmarkFormat.snapshot(verified)==BookmarkFormat.snapshot(upload)){"雲端寫入回讀不一致，請重試"}
                        check(version==epoch){"連線已取消"}
                    } // An unchanged snapshot was already read and verified above.


                    // Favorites include the current URL, custom title, scroll position, and deletion state.
                    // Use a separate namespace so older bookmark-only clients cannot erase them.
                    status="正在同步收藏與閱讀進度…"
                    val favoriteFiles=withContext(Dispatchers.IO){drive.files(FavoriteFormat.TAG)}
                    val favoriteSnapshots=withContext(Dispatchers.IO){favoriteFiles.associate{it.getString("id") to FavoriteFormat.read(drive.readRaw(it.getString("id")))}}
                    val favoriteRemote=FavoriteFormat.merge(*favoriteSnapshots.values.toTypedArray())
                    check(version==epoch){"連線已取消"}
                    if(favorites.mergeRemote(favoriteRemote))activity.controller.revision++
                    val favoriteUpload=favorites.records()
                    val favoriteRaw=FavoriteFormat.snapshot(favoriteUpload)
                    val favoriteOwn=favoriteFiles.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                    if(favoriteOwn==null||favoriteSnapshots.getValue(favoriteOwn)!=favoriteUpload){
                        val favoriteFileId=withContext(Dispatchers.IO){drive.writeRaw(store.deviceId,favoriteOwn,favoriteRaw,FavoriteFormat.TAG)}
                        check(version==epoch){"連線已取消"}
                        val favoriteVerified=withContext(Dispatchers.IO){FavoriteFormat.read(drive.readRaw(favoriteFileId))}
                        check(version==epoch){"連線已取消"}
                        check(favoriteVerified==favoriteUpload){"收藏與閱讀進度寫入回讀不一致，請重試"}
                    }


                    val browserStore=activity.controller.store
                    var siteCount:Int?=null
                    var sitesChangedWhileUploading=false
                    if(browserStore.syncSiteSettings){
                        status="正在同步天眼網站設定…"
                        val siteFiles=withContext(Dispatchers.IO){drive.files(SiteSettingsFormat.TAG)}
                        val siteSnapshots=withContext(Dispatchers.IO){siteFiles.associate{it.getString("id") to SiteSettingsFormat.read(drive.readRaw(it.getString("id")))}}
                        val siteRemote=SiteSettingsFormat.merge(*siteSnapshots.values.toTypedArray())
                        check(version==epoch){"連線已取消"}
                        val changed=browserStore.mergeSiteRecords(siteRemote)
                        if(changed.isNotEmpty())activity.controller.refreshScripts(emptyList(),applyToPage=false)
                        val siteUpload=SiteSettingsFormat.write(browserStore.siteRecords())
                        val siteOwn=siteFiles.find{it.optJSONObject("appProperties")?.optString("device")==store.deviceId}?.getString("id")
                        if(siteOwn==null||SiteSettingsFormat.write(siteSnapshots.getValue(siteOwn))!=siteUpload){
                            val siteFileId=withContext(Dispatchers.IO){drive.writeRaw(store.deviceId,siteOwn,siteUpload,SiteSettingsFormat.TAG)}
                            check(version==epoch){"連線已取消"}
                            val siteVerified=withContext(Dispatchers.IO){SiteSettingsFormat.write(SiteSettingsFormat.read(drive.readRaw(siteFileId)))}
                            check(version==epoch){"連線已取消"}
                            check(siteVerified==siteUpload){"天眼設定寫入回讀不一致，請重試"}
                        }
                        sitesChangedWhileUploading=SiteSettingsFormat.write(browserStore.siteRecords())!=siteUpload
                        siteCount=browserStore.all().count{it.rules.isNotEmpty()||it.edits.isNotEmpty()||it.css.isNotBlank()||it.js.isNotBlank()||it.html.isNotBlank()||it.guard||it.unlockScroll}
                    }
                    store.accountId=id;store.accountLabel=label;store.connected=true;connected=true;store.lastSync=System.currentTimeMillis()
                    status="已同步 ${store.visible().size} 個書籤、${favorites.all().size} 個收藏與閱讀進度"+(siteCount?.let{"、$it 個網站設定；重新載入頁面後套用"}?:"");activity.controller.revision++
                    clearProblem();lastSuccess=android.os.SystemClock.elapsedRealtime()
                    // A user may save/delete a favorite while any network request is in flight.
                    // Do not replace those local edits with the older verified upload.
                    if(sitesChangedWhileUploading||favorites.records()!=favoriteUpload||BookmarkFormat.snapshot(store.all())!=BookmarkFormat.snapshot(upload))changed()
                }catch(e:CancellationException){throw e}
                catch(e:Exception){if(version==epoch)fail(if(e is ApiException)apiProblem(e.statusCode)else SyncProblem.from(e))}
                finally{if(version==epoch){busy=false;currentDrive=null}}
            }
        }
    }
    fun disconnect(){epoch++;consentEpoch=null;currentDrive?.cancel();currentDrive=null;pending?.cancel();store.connected=false;connected=false;busy=false;authorizing=false;lastStart=null;lastSuccess=null;clearProblem();status="已停止同步，本機與雲端書籤、收藏均保留"}
    fun destroy(){epoch++;currentDrive?.cancel();tasks.cancel();store.onChange=null;favorites.onChange=null;activity.controller.store.onSiteChange=null}
}
