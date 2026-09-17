package tw.techtarian.browser

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TabPreviewStoreTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix=TabPreviewStore.newKey()
    private val root=File(base.noBackupFilesDir,"preview-test-$suffix")
    private val names=mutableSetOf<String>()
    private val context=object:ContextWrapper(base){
        override fun getNoBackupFilesDir():File=root
        override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
            val isolated="$name-$suffix";names.add(isolated)
            return base.getSharedPreferences(isolated,mode)
        }
    }
    private lateinit var store:TabPreviewStore
    @Before fun setup(){store=TabPreviewStore(context)}
    @After fun cleanup(){assertTrue(store.flush());root.deleteRecursively();names.forEach{base.deleteSharedPreferences(it)}}
    private fun image(color:Int)=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
    private fun load(key:String?):Bitmap? {
        val done=CountDownLatch(1);val result=AtomicReference<Bitmap?>()
        TabPreviewStore(context).load(key){result.set(it);done.countDown()}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get()
    }
    @Test fun thumbnailSurvivesANewStoreInstance(){
        val key=TabPreviewStore.newKey();val bitmap=image(Color.RED)
        store.save(key,bitmap);assertTrue(store.flush())
        val restored=load(key)!!;assertTrue(bitmap.sameAs(restored));restored.recycle();bitmap.recycle()
    }
    @Test fun deletingOneIdentityPreservesOtherTabsAtTheSameUrl(){
        val a=TabPreviewStore.newKey();val b=TabPreviewStore.newKey()
        val red=image(Color.RED);val blue=image(Color.BLUE)
        store.save(a,red);store.save(b,blue);store.remove(a);assertTrue(store.flush())
        assertNull(load(a));val retained=load(b)!!;assertTrue(blue.sameAs(retained))
        retained.recycle();red.recycle();blue.recycle()
    }
    @Test fun closedTabsCannotBeResurrectedByQueuedWrites(){
        val key=TabPreviewStore.newKey();val bitmap=image(Color.RED)
        repeat(3){store.save(key,bitmap)};store.remove(key);assertTrue(store.flush())
        assertNull(load(key));assertFalse(File(store.directory,"$key.png").exists());bitmap.recycle()
    }
    @Test fun onlyOrphansArePrunedAfterFullSessionRestore(){
        val keys=List(3){TabPreviewStore.newKey()};val bitmap=image(Color.BLUE)
        keys.forEach{store.save(it,bitmap)};store.retainOnly(keys.take(2).toSet());assertTrue(store.flush())
        keys.take(2).forEach{val restored=load(it);assertNotNull(restored);restored?.recycle()}
        assertNull(load(keys[2]));bitmap.recycle()
    }
    @Test fun corruptThumbnailIsOptionalNotAFatalSessionError(){
        val key=TabPreviewStore.newKey();store.directory.mkdirs()
        File(store.directory,"$key.png").writeText("broken PNG")
        assertNull(load(key))
    }
    @Test fun incompleteAtomicReplacementPreservesLastGoodPreview(){
        val key=TabPreviewStore.newKey();val bitmap=image(Color.BLUE)
        store.save(key,bitmap);assertTrue(store.flush())
        val atomic=AtomicFile(File(store.directory,"$key.png"));val output=atomic.startWrite()
        output.write(byteArrayOf(1,2,3));atomic.failWrite(output)
        val restored=load(key)!!;assertTrue(bitmap.sameAs(restored));restored.recycle();bitmap.recycle()
    }
    @Test fun incognitoAndUnsafeKeysNeverWriteFiles(){
        val bitmap=image(Color.RED)
        listOf(null,"../../outside","", "https://private.invalid/").forEach{store.save(it,bitmap);store.remove(it)}
        assertTrue(store.flush());assertTrue(store.directory.listFiles().isNullOrEmpty());bitmap.recycle()
    }
    @Test fun legacyDuplicateUrlsReceiveDistinctStableIdentitiesWithoutLosingFavorites(){
        val url="https://example.com/same"
        val preferences=context.getSharedPreferences("browser-v1",Context.MODE_PRIVATE)
        preferences.edit().putString("tabs","[\"$url\",\"$url\"]")
            .putString("tab-favorite-links","[{\"url\":\"$url\",\"id\":\"favorite-a\"},{\"url\":\"$url\",\"id\":\"favorite-b\"}]").commit()
        val browser=BrowserStore(context);val rows=browser.savedTabRecords()
        assertEquals(2,rows.size);assertNotEquals(rows[0].key,rows[1].key)
        assertEquals(listOf("favorite-a","favorite-b"),rows.map{it.favoriteId})
        assertTrue(browser.saveTabs(rows.map{it.url},rows.map{it.favoriteId},rows.map{it.key},listOf("第一頁","第二頁")))
        val restored=BrowserStore(context).savedTabRecords()
        assertEquals(rows.map{it.key},restored.map{it.key});assertEquals(listOf("第一頁","第二頁"),restored.map{it.title})
    }
}
