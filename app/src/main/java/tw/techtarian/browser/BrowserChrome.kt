package tw.techtarian.browser

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun BrowserAddressBar(
    address:TextFieldState,editing:Boolean,onFocus:(Boolean)->Unit,
    loading:Boolean,secure:Boolean,blank:Boolean,tabs:Int,certificateWarning:Boolean=false,certificateException:Boolean=false,
    onGo:()->Unit,onSecurity:()->Unit,onReload:()->Unit,onTabs:()->Unit,onNewTab:()->Unit,
    showHome:Boolean=false,onHome:()->Unit={},
){
    val colors=MaterialTheme.colorScheme
    // State-based input keeps the dragged handle and horizontal scroll in sync.
    LaunchedEffect(editing,address){if(editing)address.edit{selectAll()}}
    Row(Modifier.fillMaxWidth().testTag("browser-topbar").padding(horizontal=if(showHome)8.dp else 24.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        if(showHome)IconButton(onClick=onHome,modifier=Modifier.size(48.dp).testTag("home-button")){
            Icon(Icons.Outlined.Home,bt(R.string.msg_a782f2754c83),Modifier.size(22.dp))
        }
        // Visual capsule is 44 dp. The centered controls retain a 48 dp touch target.
        Box(Modifier.weight(1f).height(48.dp),contentAlignment=Alignment.Center){
            Surface(Modifier.fillMaxWidth().height(44.dp).testTag("address-capsule"),shape=RoundedCornerShape(22.dp),color=colors.surface){}
            Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onSecurity,modifier=Modifier.size(48.dp)){
                    Icon(if(certificateWarning||certificateException)Icons.Outlined.WarningAmber else if(secure)Icons.Outlined.Lock else if(blank)Icons.Outlined.Search else Icons.Outlined.WarningAmber,
                        if(certificateException)bt(R.string.msg_b09f65e3a56e)else if(certificateWarning)bt(R.string.msg_7c24b540153b)else bt(R.string.msg_58f5b551d138),Modifier.size(20.dp),
                        tint=if(certificateWarning||certificateException)colors.error else colors.onSurface)
                }
                BasicTextField(
                    state=address,
                    modifier=Modifier.weight(1f).height(44.dp).testTag("address-input").onFocusChanged{onFocus(it.isFocused)},
                    lineLimits=TextFieldLineLimits.SingleLine,
                    textStyle=TextStyle(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr,color=colors.onSurface,fontSize=14.sp,lineHeight=20.sp,
                        platformStyle=PlatformTextStyle(includeFontPadding=false),
                        lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.Both)),
                    cursorBrush=SolidColor(colors.primary),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),
                    onKeyboardAction={onGo()},
                    decorator={inner->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterStart){
                        if(address.text.isEmpty())Text(bt(R.string.msg_ac43b65a5965),color=colors.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
                        inner()
                    }},
                )
                IconButton(onClick=if(editing)onGo else onReload,modifier=Modifier.size(48.dp)){
                    Icon(if(editing)Icons.AutoMirrored.Outlined.ArrowForward else if(loading)Icons.Outlined.Close else Icons.Outlined.Refresh,
                        if(editing)bt(R.string.msg_fa96079c3b2f)else if(loading)bt(R.string.msg_f5d0ca4834db)else bt(R.string.msg_5f451c71b46a),Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick=onNewTab,modifier=Modifier.size(48.dp).testTag("new-tab-button")){
            Icon(Icons.Outlined.Add,bt(R.string.msg_a38d62ae74d4),Modifier.size(24.dp))
        }
        TabCountButton(tabs,onTabs)
    }
}

@Composable internal fun TabCountButton(count:Int,onClick:()->Unit){
    val color=MaterialTheme.colorScheme.onSurface
    val density=LocalDensity.current
    val numeral=remember(count,density.density,density.fontScale){
        with(density){TabNumeral(count.toString(),12.sp.toPx(),16.dp.toPx())}
    }
    Box(Modifier.size(48.dp).testTag("tab-switcher").semantics{contentDescription=bt(R.string.msg_75bc594490fc ,count)}.clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center){
        Canvas(Modifier.size(24.dp).testTag("tab-count-badge")){
            val stroke=1.5.dp.toPx()
            drawRoundRect(color,topLeft=androidx.compose.ui.geometry.Offset(stroke/2,stroke/2),
                size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),cornerRadius=CornerRadius(5.dp.toPx()),style=Stroke(stroke))
            numeral.paint.color=color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(numeral.value,center.x-numeral.horizontalCenter,
                center.y-(numeral.ink.top+numeral.ink.bottom)/2f,numeral.paint)
        }
    }
}

/** The flag on "1" widens its bounds to the left while most of its ink remains on the right. */
private class TabNumeral(val value:String,textSize:Float,maxInk:Float){
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.textSize=textSize;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    val ink=Rect()
    val horizontalCenter:Float
    init{
        paint.getTextBounds(value,0,value.length,ink)
        val fit=minOf(1f,maxInk/ink.width().coerceAtLeast(1),maxInk/ink.height().coerceAtLeast(1))
        if(fit<1f){paint.textSize*=fit;paint.getTextBounds(value,0,value.length,ink)}
        horizontalCenter=if(value=="1")opticalCenter()else(ink.left+ink.right)/2f
    }
    private fun opticalCenter():Float{
        val padding=2
        val bitmap=Bitmap.createBitmap(ink.width().coerceAtLeast(1)+padding*2,ink.height().coerceAtLeast(1)+padding*2,Bitmap.Config.ARGB_8888)
        paint.color=android.graphics.Color.WHITE
        android.graphics.Canvas(bitmap).drawText(value,(padding-ink.left).toFloat(),(padding-ink.top).toFloat(),paint)
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        var mass=0.0;var moment=0.0
        for(i in pixels.indices){val alpha=android.graphics.Color.alpha(pixels[i]);mass+=alpha;moment+=((i%bitmap.width)+.5)*alpha}
        bitmap.recycle()
        return if(mass>0)(moment/mass-padding+ink.left).toFloat()else(ink.left+ink.right)/2f
    }
}
