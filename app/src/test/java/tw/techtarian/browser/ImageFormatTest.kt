package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class ImageFormatTest {
    @Test fun recognizesCommonFormatsByBytesNotUrlOrContentType(){
        assertEquals(ImageFormat.PNG,ImageFormat.sniff(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)))
        assertEquals(ImageFormat.JPEG,ImageFormat.sniff(byteArrayOf(255.toByte(),216.toByte(),255.toByte(),224.toByte())))
        assertEquals(ImageFormat.GIF,ImageFormat.sniff("GIF89a".toByteArray()))
        assertEquals(ImageFormat.WEBP,ImageFormat.sniff("RIFFxxxxWEBPVP8 ".toByteArray()))
        assertEquals(ImageFormat.AVIF,ImageFormat.sniff("xxxxftypavifxxxxmif1".toByteArray()))
        assertEquals(ImageFormat.HEIC,ImageFormat.sniff("xxxxftypheicxxxxmif1".toByteArray()))
    }
    @Test fun htmlAndGenericBinaryNeverMasqueradeAsImages(){
        listOf("<html>403 Forbidden</html>","{\"error\":true}","PK\u0003\u0004","not an image","").forEach{assertNull(ImageFormat.sniff(it.toByteArray()))}
    }
    @Test fun actualFormatReplacesBinAndWrongExtensions(){
        assertEquals("download-abc.webp",ImageFormat.WEBP.filename("download.bin","abc"))
        assertEquals("picture-abc.png",ImageFormat.PNG.filename("picture.jpg","abc"))
        assertEquals("opaque=w600-abc.jpg",ImageFormat.JPEG.filename("opaque=w600","abc"))
        assertEquals("image-abc.gif",ImageFormat.GIF.filename("","abc"))
    }
    @Test fun filenamesRemainSafeAndBounded(){
        val name=ImageFormat.PNG.filename("../../圖片".repeat(100)+".bin","a".repeat(32))
        assertTrue(name.endsWith(".png"));assertFalse(name.contains('/'));assertFalse(name.contains('\\'))
        assertTrue(name.toByteArray().size<220)
    }
}
