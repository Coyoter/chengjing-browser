package tw.techtarian.browser

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BrowserLaunchSurfaceTest {
    @Test fun renderLaunchArtworkAcrossDisplayConfigurations(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val directory=File(context.getExternalFilesDir(null),"visual-checks").apply{mkdirs()}
        instrumentation.runOnMainSync{
            listOf(
                Triple("light-portrait",false,1f),
                Triple("dark-portrait",true,1f),
                Triple("light-large-text",false,2f),
                Triple("dark-landscape",true,1f),
            ).forEach{(name,dark,fontScale)->
                val configuration=Configuration(context.resources.configuration).apply{this.fontScale=fontScale}
                val configured=context.createConfigurationContext(configuration)
                val background=if(dark)Color.rgb(15,21,19)else Color.rgb(241,238,231)
                val view=BrowserLaunchSurface(configured,background)
                val landscape=name.endsWith("landscape")
                val width=if(landscape)1280 else 720
                val height=if(landscape)720 else 1280
                view.layout(0,0,width,height)
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                assertEquals("Corner background: $name",background,bitmap.getPixel(0,0))
                assertEquals("正在開啟澄境瀏覽器",view.contentDescription)
                var brandPixels=0
                for(y in (height*.25f).toInt() until (height*.5f).toInt()){
                    for(x in (width*.13f).toInt() until (width*.87f).toInt()){
                        if(bitmap.getPixel(x,y)!=background)brandPixels++
                    }
                }
                assertTrue("Brand must be rendered: $name",brandPixels>100)
                File(directory,"launch-$name.png").outputStream().use{
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                }
                bitmap.recycle()
            }
        }
    }
}
