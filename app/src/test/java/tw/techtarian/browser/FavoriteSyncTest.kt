package tw.techtarian.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FavoriteSyncTest{
    private fun favorite(id:String="a",time:Long=10)=Favorite(id,"我的閱讀","第 1 章","https://example.com/chapter/1",400.0,.6,time)

    @Test fun snapshotRoundTripPreservesAllFields(){
        val rows=listOf(favorite(),favorite("b").copy(deleted=true))
        assertEquals(rows,FavoriteSyncFormat.read(FavoriteSyncFormat.write(rows)))
    }
    @Test fun latestEditIncludesChangedChapterAndLowerReadingPosition(){
        val old=favorite()
        val newer=old.copy(url="https://example.com/chapter/2",pageTitle="第 2 章",scrollY=30.0,progress=.1,updated=20)
        assertEquals(listOf(newer),FavoriteSyncFormat.merge(listOf(old),listOf(newer)))
    }
    @Test fun staleOfflineSnapshotCannotResurrectDeletion(){
        val old=favorite()
        val deleted=old.copy(deleted=true,updated=11)
        assertEquals(listOf(deleted),FavoriteSyncFormat.merge(listOf(deleted),listOf(old)))
    }
    @Test fun deletionWinsEqualTimestamp(){
        val old=favorite()
        val deleted=old.copy(deleted=true)
        assertEquals(listOf(deleted),FavoriteSyncFormat.merge(listOf(old),listOf(deleted)))
        assertEquals(listOf(deleted),FavoriteSyncFormat.merge(listOf(deleted),listOf(old)))
    }
    @Test fun renamedFavoriteRetainsReadingPosition(){
        val old=favorite()
        val renamed=old.copy(title="新名稱",updated=30)
        assertEquals(listOf(renamed),FavoriteSyncFormat.merge(listOf(renamed),listOf(old)))
    }
    @Test fun separatePositionsOnSameUrlKeepTheirIds(){
        assertEquals(2,FavoriteSyncFormat.merge(listOf(favorite(),favorite("b").copy(scrollY=900.0))).size)
    }
    @Test fun mergingIsIdempotentAndOrderIndependent(){
        val a=listOf(favorite(),favorite("b").copy(title="B"))
        val b=listOf(favorite().copy(title="Z"),favorite("c",30))
        val expected=FavoriteSyncFormat.merge(a,b)
        assertEquals(expected,FavoriteSyncFormat.merge(b,a))
        assertEquals(expected,FavoriteSyncFormat.merge(expected,a,b,expected))
        assertEquals(FavoriteSyncFormat.write(expected),FavoriteSyncFormat.write(expected.reversed()))
    }
    @Test fun mergingThreeDevicesIsAssociative(){
        val a=listOf(favorite())
        val b=listOf(favorite().copy(progress=.2,updated=20),favorite("b"))
        val c=listOf(favorite("b").copy(deleted=true,updated=40))
        assertEquals(FavoriteSyncFormat.merge(FavoriteSyncFormat.merge(a,b),c),FavoriteSyncFormat.merge(a,FavoriteSyncFormat.merge(b,c)))
    }
    @Test fun legacyFavoriteWithoutDeletedFlagStillLoads(){
        val legacy=favorite().json().apply{remove("deleted")}
        assertEquals(favorite(),Favorite.from(legacy))
    }
    @Test(expected=IllegalArgumentException::class) fun unsupportedSchemaIsNotTreatedAsEmpty(){
        FavoriteSyncFormat.read("{\"version\":2,\"favorites\":[]}")
    }
    @Test(expected=IllegalArgumentException::class) fun invalidIdIsRejected(){
        FavoriteSyncFormat.merge(listOf(favorite().copy(id="")))
    }
    @Test(expected=IllegalArgumentException::class) fun nonHttpUrlIsRejected(){
        FavoriteSyncFormat.merge(listOf(favorite().copy(url="file:///private/data")))
    }
    @Test(expected=IllegalArgumentException::class) fun nonFinitePositionIsRejected(){
        FavoriteSyncFormat.merge(listOf(favorite().copy(scrollY=Double.NaN)))
    }
    @Test fun emptySnapshotIsValidButDoesNotDeleteOtherDeviceData(){
        val empty=FavoriteSyncFormat.read("{\"version\":1,\"favorites\":[]}")
        assertEquals(listOf(favorite()),FavoriteSyncFormat.merge(listOf(favorite()),empty))
    }
}
