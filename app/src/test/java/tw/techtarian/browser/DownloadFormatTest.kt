package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class DownloadFormatTest {
    @Test fun supportedSourcesAreNarrow(){
        listOf("https://example.com/movie","http://example.com/file.zip","blob:https://example.com/123","data:text/plain,hello").forEach{assertTrue(DownloadFormat.supported(it))}
        listOf("file:///data/private","content://secret/file","javascript:alert(1)","intent://open","https://user:pass@example.com/file").forEach{assertFalse(DownloadFormat.supported(it))}
    }
    @Test fun correctFormatsAndNamesForCommonDownloads(){
        assertEquals("movie.mp4",DownloadFormat.filename("movie.bin",DownloadFormat.infer(byteArrayOf(0,0,0,24)+"ftypisom".toByteArray(),"application/octet-stream")))
        assertEquals("video/mp4",DownloadFormat.infer(byteArrayOf(0,0,0,24)+"ftypisom".toByteArray(),null))
        assertEquals("audio/mp4",DownloadFormat.infer(byteArrayOf(0,0,0,24)+"ftypM4A ".toByteArray(),null))
        assertEquals("report.pdf",DownloadFormat.filename("report",DownloadFormat.infer("%PDF-1.7".toByteArray(),null)))
        assertEquals("download.zip",DownloadFormat.filename(null,DownloadFormat.infer(byteArrayOf(80,75,3,4),null)))
        assertEquals("audio.mp3",DownloadFormat.filename(null,"audio/mpeg"))
        assertEquals("image/avif",DownloadFormat.infer(byteArrayOf(0,0,0,24)+"ftypavif".toByteArray(),null))
    }
    @Test fun filenamesCannotEscapeDownloadsAndMimeParametersAreRemoved(){
        assertEquals("video/mp4",DownloadFormat.mime("VIDEO/MP4; codecs=avc1"))
        assertNull(DownloadFormat.mime("text/html\r\nInjected: yes"))
        val name=DownloadFormat.filename("../../影片\u202E\n:?.mp4","video/mp4")
        assertFalse(name.contains('/'));assertFalse(name.contains('\n'));assertFalse(name.contains('\u202E'))
        assertTrue(DownloadFormat.filename("長".repeat(300)+".mp4","video/mp4").toByteArray().size<=180)
        val unicode=DownloadFormat.filename("影片😀".repeat(100)+".mp4","video/mp4")
        assertEquals(unicode,String(unicode.toByteArray(Charsets.UTF_8),Charsets.UTF_8));assertTrue(unicode.toByteArray().size<=180)
    }
    @Test fun dataDownloadsPreserveBinaryUtf8PercentAndPlus(){
        assertArrayEquals(byteArrayOf(0,1,2,-1),DataDownload("data:application/octet-stream;base64,AAEC%2Fw%3D%3D").open().use{it.readBytes()})
        assertEquals("中文+文字",DataDownload("data:text/plain;charset=utf-8,%E4%B8%AD%E6%96%87+文字").open().use{String(it.readBytes(),Charsets.UTF_8)})
        assertEquals("hello",DataDownload("data:;base64,aGVs%0AbG8=").open().use{String(it.readBytes())})
        assertEquals("text/plain",DataDownload("data:,hello").mime)
        assertEquals(0,DataDownload("data:text/plain,").open().use{it.readBytes().size})
    }
    @Test fun malformedDataDoesNotSilentlyProduceAnotherFile(){
        assertThrows(Exception::class.java){DataDownload("data:text/plain,%GG").open().use{it.readBytes()}}
        assertThrows(Exception::class.java){DataDownload("data:text/plain;base64,%%%!").open().use{it.readBytes()}}
    }
}
