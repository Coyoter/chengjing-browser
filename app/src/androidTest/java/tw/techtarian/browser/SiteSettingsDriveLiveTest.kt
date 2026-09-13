package tw.techtarian.browser
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.TimeUnit
class SiteSettingsDriveLiveTest {
    @Test fun twoSyntheticDeviceSnapshotsRoundTripThroughGoogleDrive(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveSiteGoogle")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val authorization=Tasks.await(Identity.getAuthorizationClient(context).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(BookmarkSync.SCOPE))).build()),30,TimeUnit.SECONDS)
        check(!authorization.hasResolution()&&!authorization.accessToken.isNullOrBlank()){ "Existing Google authorization required; no account data changed" }
        val drive=BookmarkDrive(authorization.accessToken!!);val tag="chengjing-browser-site-qa-"+UUID.randomUUID();val created=mutableListOf<Pair<String,String>>()
        try{
            val a=SiteSettingsRecord(SiteRules("example.com",css="p{line-height:1.8}",edits=listOf(PageEdit(id="qa",selector="#card",html="<p>閱讀提醒</p>"))),10)
            val first=drive.writeRaw("qa-a",null,SiteSettingsFormat.write(listOf(a)),tag);created.add("qa-a" to first)
            assertEquals(listOf(a),SiteSettingsFormat.read(drive.readRaw(first)))
            val deleted=a.copy(settings=SiteRules("example.com"),modified=20)
            val second=drive.writeRaw("qa-b",null,SiteSettingsFormat.write(listOf(deleted)),tag);created.add("qa-b" to second)
            val files=drive.files(tag);assertEquals(2,files.size)
            val merged=SiteSettingsFormat.merge(*files.map{SiteSettingsFormat.read(drive.readRaw(it.getString("id")))}.toTypedArray())
            assertEquals(listOf(deleted),merged)
            drive.writeRaw("qa-a",first,SiteSettingsFormat.write(merged),tag)
            assertEquals(listOf(deleted),SiteSettingsFormat.read(drive.readRaw(first)))
        }finally{
            created.forEach{(device,id)->drive.writeRaw(device,id,SiteSettingsFormat.write(emptyList()),tag)}
            drive.cancel()
        }
    }
}
