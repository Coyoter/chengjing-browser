package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class PreviewPixelsTest {
    @Test fun flatWhiteBlackAndTransparentFramesAreNotPreviews(){
        listOf(-1,0xff101010.toInt(),0).forEach{assertFalse(PreviewPixels.hasContent(IntArray(300*400){it},300,400))}
    }
    @Test fun anEdgeScrollbarCannotTurnAnEmptyFrameIntoAValidSnapshot(){
        val pixels=IntArray(300*400){-1}
        for(y in 10..300)for(x in 298..299)pixels[y*300+x]=0xff444444.toInt()
        assertFalse(PreviewPixels.hasContent(pixels,300,400))
    }
    @Test fun textOrMonochromeComicLinesAreEnoughToKeepTheFrame(){
        val pixels=IntArray(300*400){-1}
        pixels[100*300+100]=0xff111111.toInt()
        assertTrue(PreviewPixels.hasContent(pixels,300,400))
    }
    @Test fun tinyColorNoiseDoesNotOverwriteAUsefulPreview(){
        val pixels=IntArray(300*400){if(it%2==0)-1 else 0xfffcfcfc.toInt()}
        assertFalse(PreviewPixels.hasContent(pixels,300,400))
    }
}
