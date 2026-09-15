package tw.techtarian.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FavoriteSyncFormatTest {
    private fun item(id:String="book",updated:Long=10,progress:Double=.7)=
        Favorite(id,"我的小說","第七章","https://example.com/chapter7",700.0,progress,updated)

    @Test fun roundTripPreservesIdentityTitlesUrlAndPosition(){
        val original=item()
        assertEquals(listOf(original),FavoriteFormat.read(FavoriteFormat.write(listOf(original))))
    }
    @Test fun legacyRecordsWithoutDeletedRemainVisible(){
        val json=item().json().apply{remove("deleted")}
        assertEquals(item(),Favorite.from(json))
    }
    @Test fun latestSavedPositionWinsEvenWhenReadingBackwards(){
        val old=item(progress=.9)
        val recent=old.copy(updated=11,progress=.2,scrollY=200.0)
        assertEquals(listOf(recent),FavoriteFormat.merge(listOf(old),listOf(recent)))
    }
    @Test fun deletionDoesNotResurrectFromAnOlderOfflineSnapshot(){
        val original=item()
        val deleted=original.copy(updated=11,deleted=true)
        val first=FavoriteFormat.merge(listOf(deleted),listOf(original))
        assertTrue(first.single().deleted)
        assertEquals(first,FavoriteFormat.merge(first,listOf(original)))
        assertEquals(first,FavoriteFormat.read(FavoriteFormat.write(first)))
    }
    @Test fun equalClocksResolveToDeletionRegardlessOfDeviceOrder(){
        val original=item()
        val deleted=original.copy(deleted=true)
        assertEquals(listOf(deleted),FavoriteFormat.merge(listOf(original),listOf(deleted)))
        assertEquals(listOf(deleted),FavoriteFormat.merge(listOf(deleted),listOf(original)))
    }
    @Test fun equalClockEditsAreDeterministic(){
        val a=item().copy(title="A")
        val b=item().copy(title="B")
        assertEquals(FavoriteFormat.merge(listOf(a),listOf(b)),FavoriteFormat.merge(listOf(b),listOf(a)))
    }
    @Test fun mergeIsAssociativeAndIdempotent(){
        val a=listOf(item("a",10))
        val b=listOf(item("a",12),item("b",11))
        val c=listOf(item("b",13).copy(deleted=true),item("c",14))
        val result=FavoriteFormat.merge(a,b,c)
        assertEquals(result,FavoriteFormat.merge(FavoriteFormat.merge(a,b),c))
        assertEquals(result,FavoriteFormat.merge(a,FavoriteFormat.merge(b,c)))
        assertEquals(result,FavoriteFormat.merge(result,result))
    }
    @Test fun distinctFavoriteIdentitiesAreNotDeduplicatedByUrl(){
        assertEquals(2,FavoriteFormat.merge(listOf(item("first")),listOf(item("second"))).size)
    }
    @Test fun emptyDeviceDoesNotEraseOtherDevice(){
        assertEquals(listOf(item()),FavoriteFormat.merge(emptyList(),listOf(item())))
    }
    @Test fun captureAdvancesClockEvenIfWallClockMovesBack(){
        val original=item(updated=100)
        val saved=Favorite.capture(original,original.url,original.pageTitle,20.0,.02,now=1)
        assertEquals(101L,saved.updated)
        assertEquals(original.id,saved.id)
        assertEquals(original.title,saved.title)
    }
    @Test fun oldWriteInFlightCannotReplaceANewerLocalSave(){
        val inFlight=item(updated=20)
        val local=inFlight.copy(updated=21,progress=.3,scrollY=300.0)
        assertEquals(listOf(local),FavoriteFormat.merge(listOf(local),listOf(inFlight)))
    }
    @Test(expected=IllegalArgumentException::class)
    fun futureSchemaIsRejectedInsteadOfDroppingData(){
        val json=JSONObject(FavoriteFormat.write(listOf(item()))).put("schema",2)
        FavoriteFormat.read(json.toString())
    }
    @Test(expected=IllegalArgumentException::class)
    fun otherCollectionIsRejected(){
        FavoriteFormat.read(JSONObject(FavoriteFormat.write(listOf(item()))).put("app","bookmarks").toString())
    }
    @Test(expected=IllegalArgumentException::class)
    fun invalidFavoriteRejectsSnapshot(){
        val json=JSONObject(FavoriteFormat.write(listOf(item())))
        json.getJSONArray("favorites").getJSONObject(0).put("url","javascript:alert(1)")
        FavoriteFormat.read(json.toString())
    }
}
