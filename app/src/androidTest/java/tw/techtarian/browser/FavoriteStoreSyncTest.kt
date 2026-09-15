package tw.techtarian.browser

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FavoriteStoreSyncTest{
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val names=mutableListOf<String>()
    private fun device():Context{
        val suffix="favorite-sync-test-"+UUID.randomUUID().toString()
        return object:ContextWrapper(base){
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences{
                val isolated="$name-$suffix"
                if(isolated !in names)names.add(isolated)
                return base.getSharedPreferences(isolated,mode)
            }
        }
    }
    @After fun cleanup(){names.forEach{base.deleteSharedPreferences(it)}}

    @Test fun oldItemsMigrateWithoutChangingIdsOrPositions(){
        val context=device()
        val old=Favorite("legacy","收藏","章節","https://example.com/",123.0,.3,10)
        val legacy=old.json().apply{remove("deleted")}
        assertTrue(context.getSharedPreferences("favorites-v1",Context.MODE_PRIVATE).edit().putString("items",JSONArray().put(legacy).toString()).commit())
        val store=FavoriteStore(context)
        assertEquals(old,store.get(old.id))
        assertEquals(listOf(old),FavoriteSyncFormat.read(FavoriteSyncFormat.write(store.syncRecords())))
    }
    @Test fun twoDevicesMergeProgressRenameAndDeleteWithoutResurrection(){
        val a=FavoriteStore(device())
        val b=FavoriteStore(device())
        val saved=a.save("https://example.com/1","第一章",200.0,.4)
        b.mergeRemote(a.syncRecords())
        val moved=b.save("https://example.com/2","第二章",50.0,.1,updateId=saved.id)
        a.mergeRemote(b.syncRecords())
        assertEquals(moved,a.get(saved.id))
        a.rename(saved.id,"自訂名稱")
        b.mergeRemote(a.syncRecords())
        assertEquals("自訂名稱",b.get(saved.id)?.title)
        assertEquals(50.0,b.get(saved.id)!!.scrollY,0.0)
        val stale=b.syncRecords()
        a.remove(saved.id)
        b.mergeRemote(a.syncRecords())
        a.mergeRemote(stale)
        assertTrue(a.all().isEmpty())
        assertTrue(b.all().isEmpty())
        assertTrue(a.syncRecords().single().deleted)
    }
    @Test fun localEditAfterSnapshotIsPreservedAndRequiresAnotherUpload(){
        val store=FavoriteStore(device())
        val saved=store.save("https://example.com/","章節",0.0,0.0)
        val upload=FavoriteSyncFormat.write(store.syncRecords())
        store.save(saved.url,saved.pageTitle,900.0,.9,updateId=saved.id)
        store.mergeRemote(FavoriteSyncFormat.read(upload))
        assertEquals(.9,store.get(saved.id)!!.progress,0.0)
        assertNotEquals(upload,FavoriteSyncFormat.write(store.syncRecords()))
    }
    @Test fun onlyLocalChangesNotifySync(){
        val store=FavoriteStore(device())
        var changes=0
        store.onChange={changes++}
        val saved=store.save("https://example.com/","章節",10.0,.2)
        store.rename(saved.id,"我的收藏")
        store.mergeRemote(store.syncRecords())
        store.remove(saved.id)
        assertEquals(3,changes)
    }
}
