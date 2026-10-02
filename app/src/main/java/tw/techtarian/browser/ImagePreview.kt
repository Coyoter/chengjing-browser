package tw.techtarian.browser

import android.annotation.SuppressLint
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

@Composable internal fun ImagePreviewDialog(c:BrowserController,asset:ImageAsset){
    Dialog(onDismissRequest={c.imagePreview=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        val view=LocalView.current
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->
            browserSystemBars(window,view,false)
            if(asset.private)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }}
        ImageViewer(c,asset,Modifier.fillMaxSize()){c.imagePreview=null}
    }
}
@Composable internal fun ImageViewer(c:BrowserController,asset:ImageAsset,modifier:Modifier=Modifier,temporaryTab:Boolean=false,onClose:()->Unit){
    var drawable by remember(asset.file.path){mutableStateOf<android.graphics.drawable.Drawable?>(null)}
    var error by remember(asset.file.path){mutableStateOf("")}
    LaunchedEffect(asset.file.path){
        runCatching{withContext(Dispatchers.IO){ImageDecoder.decodeDrawable(ImageDecoder.createSource(asset.file)){decoder,info,_->
            val scale=min(1.0,min(4096.0/maxOf(info.size.width,info.size.height),sqrt(4_000_000.0/(info.size.width.toDouble()*info.size.height))))
            decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()))
        }}}.onSuccess{drawable=it}.onFailure{error=bt(R.string.msg_76d26aa8c33e)}
    }
    DisposableEffect(drawable){val animation=drawable as? AnimatedImageDrawable;animation?.start();onDispose{animation?.stop()}}
    Column(modifier.background(Color(0xff101315)).safeDrawingPadding().testTag("image-preview")){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(bt(R.string.msg_3b0e45edcfb7),Modifier.weight(1f).padding(12.dp),color=Color.White,style=MaterialTheme.typography.titleMedium)
            IconButton(onClick=onClose){Icon(Icons.Outlined.Close,bt(R.string.msg_18ff1017a7e0),tint=Color.White)}
        }
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            if(error.isNotEmpty())Text(error,Modifier.padding(24.dp),color=Color.White)
            else if(drawable==null)CircularProgressIndicator()
            else AndroidView(factory={ZoomImageView(it)},update={it.show(drawable!!)},modifier=Modifier.fillMaxSize().testTag("image-preview-content"))
        }
        Text(bt(R.string.msg_b14f4f9c10dc ,asset.width,asset.height,asset.format.extension.uppercase()),Modifier.padding(horizontal=20.dp,vertical=8.dp),color=Color(0xffc3cbc7),style=MaterialTheme.typography.bodySmall)
        if(temporaryTab)Text(bt(R.string.msg_c660e164fa68),Modifier.padding(horizontal=20.dp,vertical=4.dp),color=Color(0xffc3cbc7),style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
            listOf(ImageAction.COPY to bt(R.string.msg_720b7e9d72fa),ImageAction.DOWNLOAD to bt(R.string.msg_b3436480e1ad),ImageAction.SHARE to bt(R.string.msg_750f7a0175cc)).forEach{(action,label)->
                TextButton(onClick={(c.context as MainActivity).imageActions.usePrepared(asset,action)},enabled=drawable!=null,
                    colors=ButtonDefaults.textButtonColors(contentColor=Color(0xff69dfc0),disabledContentColor=Color(0xffc3cbc7).copy(alpha=.38f))){Text(label)}
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
private class ZoomImageView(context:android.content.Context):androidx.appcompat.widget.AppCompatImageView(context){
    private val transform=Matrix();private var minimum=1f;private var current=1f
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean{zoom((current*detector.scaleFactor).coerceIn(minimum,minimum*5),detector.focusX,detector.focusY);return true}
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent)=true
        override fun onScroll(e1:MotionEvent?,e2:MotionEvent,dx:Float,dy:Float):Boolean{if(!scale.isInProgress){transform.postTranslate(-dx,-dy);constrain()};return true}
        override fun onDoubleTap(e:MotionEvent):Boolean{if(current>minimum*1.1f)fit()else zoom(minimum*2.5f,e.x,e.y);return true}
    })
    init{scaleType=ScaleType.MATRIX;contentDescription=bt(R.string.msg_9ef754f8e3fd)}
    fun show(value:android.graphics.drawable.Drawable){if(drawable!==value){setImageDrawable(value);fit()}}
    // Compose's AndroidView holder does not clip an ImageView's zoom matrix.
    // Clip in local canvas coordinates so the image cannot cover the controls.
    override fun onDraw(canvas:android.graphics.Canvas){val saved=canvas.save();canvas.clipRect(0,0,width,height);super.onDraw(canvas);canvas.restoreToCount(saved)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);fit()}
    private fun fit(){val image=drawable?:return;if(width<=0||height<=0||image.intrinsicWidth<=0||image.intrinsicHeight<=0)return
        minimum=min(width.toFloat()/image.intrinsicWidth,height.toFloat()/image.intrinsicHeight);current=minimum
        transform.reset();transform.postScale(minimum,minimum);constrain()
    }
    private fun zoom(value:Float,x:Float,y:Float){transform.postScale(value/current,value/current,x,y);current=value;constrain()}
    private fun constrain(){val image=drawable?:return;val rect=RectF(0f,0f,image.intrinsicWidth.toFloat(),image.intrinsicHeight.toFloat());transform.mapRect(rect)
        val dx=if(rect.width()<width)(width-rect.width())/2-rect.left else if(rect.left>0)-rect.left else if(rect.right<width)width-rect.right else 0f
        val dy=if(rect.height()<height)(height-rect.height())/2-rect.top else if(rect.top>0)-rect.top else if(rect.bottom<height)height-rect.bottom else 0f
        transform.postTranslate(dx,dy);imageMatrix=transform
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{scale.onTouchEvent(event);gestures.onTouchEvent(event);if(event.actionMasked==MotionEvent.ACTION_UP)performClick();return true}
    override fun performClick():Boolean{super.performClick();return true}
}
