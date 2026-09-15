package tw.techtarian.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FavoriteSyncTest {
    private fun item(id:String="novel",time:Long=100,progress:Double=.8)=
        Favorite(id,"我的小說","第八章","https://example.com/chapter8",860.5,progress,time)

    @Test fun snapshotPreservesEveryFavoriteField(){
        val original=item()
        assertEquals(listOf(original),FavoriteFormat.read(FavoriteFormat.snapshot(listOf(original))))
    }
    @Test fun latestWholeRecordWinsEvenWhenReadingBackwards(){
        val old=item()
        val newer=old.copy(url="https://example.com/chapter2",pageTitle="第二章",scrollY=75.25,progress=.1,updated=101)
        assertEquals(listOf(newer),FavoriteFormat.merge(listOf(old),listOf(newer)))
        assertEquals(listOf(newer),FavoriteFormat.merge(listOf(newer),listOf(old)))
    }
    @Test fun captureKeepsIdentityAndCustomTitleAcrossChapters(){
        val old=item()
        val next=Favorite.capture(old,"https://example.com/chapter9","第九章",10.0,.01,101)
        assertEquals(old.id,next.id)
        assertEquals(old.title,next.title)
        assertEquals("第九章",next.pageTitle)
        assertEquals("https://example.com/chapter9",next.url)
        assertEquals(.01,next.progress,0.0)
    }
    @Test fun deletionSurvivesAnOfflineDevicesOldSnapshot(){
        val old=item()
        val deleted=old.copy(deleted=true,updated=102)
        val cloud=FavoriteFormat.read(FavoriteFormat.snapshot(listOf(deleted)))
        val merged=FavoriteFormat.merge(cloud,listOf(old))
        assertEquals(listOf(deleted),merged)
        assertTrue(merged.filterNot{it.deleted}.isEmpty())
    }
    @Test fun deletionWinsAtTheSameTimestamp(){
        val edited=item().copy(title="新版名稱")
        val deleted=item().copy(deleted=true)
        assertEquals(listOf(deleted),FavoriteFormat.merge(listOf(edited),listOf(deleted)))
        assertEquals(listOf(deleted),FavoriteFormat.merge(listOf(deleted),listOf(edited)))
    }
    @Test fun equalClockEditsConvergeRegardlessOfDeviceOrder(){
        val a=item().copy(title="A",scrollY=10.0)
        val b=item().copy(title="B",scrollY=20.0)
        val forward=FavoriteFormat.merge(listOf(a),listOf(b))
        val reverse=FavoriteFormat.merge(listOf(b),listOf(a))
        assertEquals(forward,reverse)
        assertEquals(forward,FavoriteFormat.merge(forward,forward))
    }
    @Test fun editsMadeDuringUploadRemainNewerThanReadback(){
        val upload=item()
        val local=upload.copy(progress=.95,scrollY=1500.0,updated=101)
        val verified=FavoriteFormat.read(FavoriteFormat.snapshot(listOf(upload)))
        assertEquals(listOf(local),FavoriteFormat.merge(listOf(local),verified))
    }
    @Test fun intentionalSeparateFavoritesAtSameUrlKeepTheirIds(){
        val a=item("a")
        val b=item("b")
        assertEquals(listOf(a,b),FavoriteFormat.merge(listOf(b),listOf(a)))
    }
    @Test fun oldLocalJsonWithoutDeletedFieldStillLoads(){
        val row=item().json().apply{remove("deleted")}
        assertEquals(item(),Favorite.from(row))
        assertFalse(Favorite.from(row).deleted)
    }
    @Test fun timestampsAdvanceEvenWhenDeviceClockMovesBackwards(){
        assertEquals(501L,FavoriteFormat.nextTimestamp(500,400))
        assertEquals(900L,FavoriteFormat.nextTimestamp(500,900))
        assertEquals(501L,Favorite.capture(item(time=500),"https://example.com/","Page",0.0,0.0,400).updated)
    }
    @Test fun mergeIsAssociativeAndSorted(){
        val a=listOf(item("b",1),item("a",2))
        val b=listOf(item("b",3))
        val c=listOf(item("a",4).copy(deleted=true))
        assertEquals(FavoriteFormat.merge(a,FavoriteFormat.merge(b,c)),FavoriteFormat.merge(FavoriteFormat.merge(a,b),c))
        assertEquals(listOf("a","b"),FavoriteFormat.merge(a,b,c).map{it.id})
    }
    @Test(expected=IllegalArgumentException::class) fun unknownSchemaIsRejected(){
        FavoriteFormat.read("{\"schema\":2,\"favorites\":[]}")
    }
    @Test(expected=IllegalArgumentException::class) fun invalidUrlIsRejected(){
        Favorite.from(item().json().put("url","file:///private/data"))
    }
    @Test(expected=IllegalArgumentException::class) fun overflowingClockIsRejected(){
        Favorite.from(item().json().put("updated",Long.MAX_VALUE))
    }
}
