package tw.techtarian.browser
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class SiteSettingsStoreTest {
    @Test fun localChangesWinAfterRemoteMergeAndExceptionsStayLocal(){
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-site-settings-"+UUID.randomUUID()
        val ctx=object:ContextWrapper(base){override fun getSharedPreferences(ignored:String,mode:Int)=base.getSharedPreferences(name,mode)}
        try{
            val store=BrowserStore(ctx);assertFalse(store.syncSiteSettings)
            store.setCertificateException("https://example.com",true)
            val cloud=SiteSettingsRecord(SiteRules("example.com",css="p{color:green}",edits=listOf(PageEdit(id="cloud",selector="#card",html="<p>雲端提醒</p>"))),System.currentTimeMillis()+5000)
            store.mergeSiteRecords(listOf(cloud));assertEquals(cloud.settings,store.get("example.com"))
            store.save(SiteRules("example.com"))
            assertTrue(store.siteRecords().single().modified>cloud.modified)
            store.mergeSiteRecords(listOf(cloud));assertTrue(store.get("example.com").edits.isEmpty())
            assertTrue(BrowserStore(ctx).get("example.com").rules.isEmpty())
            assertTrue(store.certificateException("https://example.com"))
            assertFalse(SiteSettingsFormat.write(store.siteRecords()).contains("certificate"))
            assertTrue(store.undo("example.com"));assertEquals(cloud.settings,store.get("example.com"))
        }finally{base.deleteSharedPreferences(name)}
    }
}
