package tw.techtarian.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FavoriteSyncStoreTest {
    private lateinit var context:Context
    private lateinit var store:FavoriteStore
    private val preferenceNames=mutableSetOf<String>()
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun isolatedStore(){
        val prefix="favorite-sync-test-${UUID.randomUUID()}-"
        context=object:ContextWrapper(target){
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences{
                val isolated=prefix+name
                preferenceNames.add(isolated)
                return target.getSharedPreferences(isolated,mode)
            }
        }
        store=FavoriteStore(context)
    }
    @After fun cleanOnlyTestPreferences(){preferenceNames.forEach{target.deleteSharedPreferences(it)}}

    @Test fun legacyLibraryKeepsItsIdsAndPositions(){
        val original=Favorite("legacy","舊收藏","原頁面","https://example.com/old",145.0,.35,100)
        val legacy=original.json().apply{remove("deleted")}
        context.getSharedPreferences("favorites-v1",Context.MODE_PRIVATE).edit().putString("items",JSONArray().put(legacy).toString()).commit()
        assertEquals(listOf(original),store.all())
        val changed=store.save("https://example.com/new","新頁面",220.0,.6,original.id)
        assertEquals(original.id,changed.id)
        assertEquals(original.title,changed.title)
        assertEquals(listOf(changed),FavoriteStore(context).all())
    }
    @Test fun renameChangesTimestampAndEnqueuesSync(){
        var callbacks=0
        store.onChange={callbacks++}
        val row=store.save("https://example.com/","頁面",100.0,.2)
        store.rename(row.id,"我的收藏")
        val renamed=store.get(row.id)!!
        assertTrue(renamed.updated>row.updated)
        assertEquals("我的收藏",renamed.title)
        assertEquals(.2,renamed.progress,0.0)
        assertEquals(2,callbacks)
    }
    @Test fun persistedDeletionCannotBeRevivedByOldRemoteData(){
        var callbacks=0
        store.onChange={callbacks++}
        val row=store.save("https://example.com/","頁面",100.0,.2)
        store.remove(row.id)
        assertEquals(2,callbacks)
        val reopened=FavoriteStore(context)
        assertTrue(reopened.all().isEmpty())
        assertTrue(reopened.records().single().deleted)
        assertFalse(reopened.mergeRemote(listOf(row)))
        assertTrue(reopened.all().isEmpty())
    }
    @Test fun remoteMergeUpdatesLibraryWithoutAnUploadLoop(){
        var callbacks=0
        store.onChange={callbacks++}
        val remote=Favorite("other-phone","我的小說","第二章","https://example.com/chapter2",480.0,.45,100)
        assertTrue(store.mergeRemote(listOf(remote)))
        assertEquals(remote,store.get(remote.id))
        assertEquals(0,callbacks)
        assertFalse(store.mergeRemote(listOf(remote)))
        store.save("https://example.com/chapter3","第三章",120.0,.1,remote.id)
        assertEquals(1,callbacks)
        assertEquals("我的小說",store.get(remote.id)!!.title)
    }
    @Test fun newLocalChangeAfterRemoteUploadSnapshotIsNotLost(){
        val before=store.save("https://example.com/","頁面",100.0,.2)
        val snapshot=store.records()
        val after=store.save(before.url,before.pageTitle,500.0,.9,before.id)
        store.mergeRemote(snapshot)
        assertEquals(after,store.get(before.id))
        assertEquals(.9,FavoriteStore(context).get(before.id)!!.progress,0.0)
    }
    @Test fun explicitlySavingAnotherCopyKeepsBothFavorites(){
        val first=store.save("https://example.com/","頁面",100.0,.2)
        val second=store.save(first.url,"另一本",500.0,.9,forceNew=true)
        assertNotEquals(first.id,second.id)
        assertEquals(2,store.all().size)
    }
}
