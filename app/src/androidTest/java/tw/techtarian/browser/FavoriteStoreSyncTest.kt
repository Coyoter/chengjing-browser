package tw.techtarian.browser

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FavoriteStoreSyncTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val aName="favorite-sync-test-a-${UUID.randomUUID()}"
    private val bName="favorite-sync-test-b-${UUID.randomUUID()}"
    private lateinit var a:FavoriteStore
    private lateinit var b:FavoriteStore
    @Before fun prepare(){a=FavoriteStore(context,aName);b=FavoriteStore(context,bName)}
    @After fun cleanup(){context.deleteSharedPreferences(aName);context.deleteSharedPreferences(bName)}
    private fun transmit(from:FavoriteStore,to:FavoriteStore){
        to.mergeRemote(FavoriteFormat.read(FavoriteFormat.write(from.records())))
    }
    @Test fun existingLocalRecordsMigrateWithoutLosingProgress(){
        val original=Favorite("legacy","收藏","章節","https://example.com/1",325.0,.35,10)
        val legacy=original.json().apply{remove("deleted")}
        assertTrue(context.getSharedPreferences(aName,Context.MODE_PRIVATE).edit()
            .putString("items",JSONArray().put(legacy).toString()).commit())
        assertEquals(listOf(original),a.all())
        transmit(a,b)
        assertEquals(original,b.get("legacy"))
        assertEquals(original,FavoriteStore(context,bName).get("legacy"))
    }
    @Test fun progressRenameAndDeletionTravelBothDirections(){
        val first=a.save("https://example.com/1","第一章",100.0,.1)
        transmit(a,b)
        assertEquals(first,b.get(first.id))
        val next=b.save("https://example.com/2","第二章",800.0,.8,first.id)
        transmit(b,a)
        assertEquals(next,a.get(first.id))
        a.rename(first.id,"我的小說")
        val renamed=a.get(first.id)!!
        assertTrue(renamed.updated>next.updated)
        transmit(a,b)
        assertEquals("我的小說",b.get(first.id)!!.title)
        val stale=a.records()
        b.remove(first.id)
        transmit(b,a)
        assertNull(a.get(first.id))
        assertTrue(a.records().single().deleted)
        a.mergeRemote(stale)
        assertNull(a.get(first.id))
        assertNull(FavoriteStore(context,aName).get(first.id))
    }
    @Test fun editsMadeWhileUploadIsInFlightArePreserved(){
        val first=a.save("https://example.com/1","第一章",800.0,.8)
        val sent=a.records()
        val revised=a.save(first.url,first.pageTitle,200.0,.2,first.id)
        a.mergeRemote(sent)
        assertEquals(revised,a.get(first.id))
        transmit(a,b)
        assertEquals(.2,b.get(first.id)!!.progress,0.0)
    }
    @Test fun onlyLocalMutationsNotifySync(){
        var local=0;var remote=0
        a.onChange={local++};b.onChange={remote++}
        val first=a.save("https://example.com/1","Title",0.0,0.0)
        a.rename(first.id,"Renamed")
        transmit(a,b)
        assertEquals(2,local);assertEquals(0,remote)
        a.remove(first.id)
        transmit(a,b)
        assertEquals(3,local);assertEquals(0,remote)
    }
    @Test fun explicitNewFavoriteKeepsASeparateIdentity(){
        val first=a.save("https://example.com/1","Title",0.0,0.0)
        val other=a.save(first.url,first.title,300.0,.3,forceNew=true)
        assertNotEquals(first.id,other.id)
        transmit(a,b)
        assertEquals(2,b.all().size)
    }
}
