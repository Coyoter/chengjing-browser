package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView

/** Small visible-viewport snapshots; never ask WebView to rasterize a whole long document. */
internal object TabPreviewImage {
    fun capture(web:WebView):Bitmap? {
        if(web.width<=0||web.height<=0)return null
        val width=300
        val height=(web.height.toFloat()*width/web.width).toInt().coerceIn(1,400)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        return try{
            val canvas=Canvas(bitmap)
            val scale=width.toFloat()/web.width
            canvas.scale(scale,scale)
            // View.draw() expects the caller's viewport translation. Without this, a
            // scrolled WebView requests document-top tiles which Chromium may have evicted.
            canvas.translate(-web.scrollX.toFloat(),-web.scrollY.toFloat())
            web.draw(canvas)
            if(hasContent(bitmap))bitmap else {bitmap.recycle();null}
        }catch(_:Exception){bitmap.recycle();null}
    }
    fun hasContent(bitmap:Bitmap):Boolean {
        if(bitmap.isRecycled||bitmap.width<=0||bitmap.height<=0)return false
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        return PreviewPixels.hasContent(pixels,bitmap.width,bitmap.height)
    }
}

internal object PreviewPixels {
    /** Ignore the thin edge containing scrollbars; a flat loading frame is not a new preview. */
    fun hasContent(pixels:IntArray,width:Int,height:Int):Boolean {
        if(width<=0||height<=0||pixels.size.toLong()<width.toLong()*height)return false
        val insetX=(width/50).coerceAtMost((width-1)/2)
        val insetY=(height/100).coerceAtMost((height-1)/2)
        var first:Int?=null
        for(y in insetY until height-insetY)for(x in insetX until width-insetX){
            val pixel=pixels[y*width+x]
            if(pixel ushr 24<32)continue
            val base=first
            if(base==null){first=pixel;continue}
            if(kotlin.math.abs((pixel shr 16 and 255)-(base shr 16 and 255))>8||
                kotlin.math.abs((pixel shr 8 and 255)-(base shr 8 and 255))>8||
                kotlin.math.abs((pixel and 255)-(base and 255))>8)return true
        }
        return false
    }
}
