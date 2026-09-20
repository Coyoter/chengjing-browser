package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IconCachePolicyTest {
    @get:Rule val temp=TemporaryFolder()
    private val now=1_800_000_000_000L
    private fun file(name:String,bytes:Int,time:Long)=File(temp.root,name).apply{writeBytes(ByteArray(bytes));assertTrue(setLastModified(time))}
    @Test fun expiresThirtyDayOldAndInterruptedFilesWithoutDeletingFreshIcons(){
        val old=file("old.png",10,now-IconCachePolicy.MAX_AGE_MILLIS)
        val fresh=file("fresh.png",10,now-IconCachePolicy.MAX_AGE_MILLIS+1)
        val temporary=file("write.tmp",10,now)
        IconCachePolicy.trim(temp.root,now)
        assertFalse(old.exists());assertFalse(temporary.exists());assertTrue(fresh.exists())
    }
    @Test fun evictsOldestFilesToEightMebibytes(){
        val files=(1..10).map{file("$it.png",1024*1024,now-100+it)}
        IconCachePolicy.trim(temp.root,now)
        assertEquals(8,temp.root.listFiles()!!.size)
        assertFalse(files[0].exists());assertFalse(files[1].exists());assertTrue(files.last().exists())
        assertEquals(IconCachePolicy.MAX_BYTES,temp.root.listFiles()!!.sumOf{it.length()})
    }
    @Test fun capsFileCountEvenForTinyIcons(){
        repeat(300){file("$it.png",1,now-300+it)}
        IconCachePolicy.trim(temp.root,now)
        assertEquals(256,temp.root.listFiles()!!.size)
        assertTrue(File(temp.root,"299.png").exists());assertFalse(File(temp.root,"0.png").exists())
    }
}
