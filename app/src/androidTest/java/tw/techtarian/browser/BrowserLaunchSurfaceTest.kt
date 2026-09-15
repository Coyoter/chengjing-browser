package tw.techtarian.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrowserLaunchSurfaceTest {
    @Test fun nativeLaunchRendersInBothThemesAndOrientations(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val output=File(context.getExternalFilesDir(null),"launch-previews").apply{mkdirs()}
        instrumentation.runOnMainSync{
            for(dark in listOf(false,true))for(landscape in listOf(false,true)){
                val width=if(landscape)1920 else 1080
                val height=if(landscape)1080 else 1920
                val view=BrowserLaunchSurface(context,dark)
                assertEquals("正在開啟澄境瀏覽器",view.contentDescription)
                view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                view.layout(0,0,width,height)
                val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                try{
                    view.draw(Canvas(image))
                    val expected=if(dark)Color.rgb(15,21,19)else Color.rgb(241,238,231)
                    assertEquals(expected,image.getPixel(0,0))
                    var contentPixels=0
                    for(y in (height*.24).toInt() until (height*.72).toInt() step 3){
                        for(x in (width*.12).toInt() until (width*.86).toInt() step 3){
                            if(image.getPixel(x,y)!=expected)contentPixels++
                        }
                    }
                    assertTrue("The wordmark must not be an empty background",contentPixels>100)
                    val name="launch-${if(dark)"dark"else"light"}-${if(landscape)"landscape"else"portrait"}.png"
                    File(output,name).outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
                }finally{image.recycle()}
            }
        }
    }
}
