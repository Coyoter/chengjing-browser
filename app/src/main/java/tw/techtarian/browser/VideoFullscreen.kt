package tw.techtarian.browser

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Use the activity window so Android's Back dispatcher and video share the same window. */
@Composable internal fun VideoFullscreen(c:BrowserController,video:android.view.View){
    val host=LocalView.current
    val window=(c.context as Activity).window
    val focused=LocalWindowInfo.current.isWindowFocused
    BackHandler{c.exitFullscreen()}
    DisposableEffect(window,video){
        val originalCutout=window.attributes.layoutInDisplayCutoutMode
        val originalFlags=window.attributes.flags
        val control=WindowCompat.getInsetsController(window,host)
        val behavior=control.systemBarsBehavior
        val visible=ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.systemBars())!=false
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        control.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        control.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        onDispose{
            val added=(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) and originalFlags.inv()
            window.clearFlags(added)
            window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=originalCutout}
            control.systemBarsBehavior=behavior
            if(visible)control.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(focused){if(focused)WindowCompat.getInsetsController(window,host).hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())}
    AndroidView(factory={(video.parent as? ViewGroup)?.removeView(video);video},modifier=Modifier.fillMaxSize().background(Color.Black).testTag("video-fullscreen"))
}
