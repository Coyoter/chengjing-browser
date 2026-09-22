package tw.techtarian.browser

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One persisted choice controls both Android resources and Compose surfaces. */
internal object BrowserAppearance {
    fun mode(choice:String)=when(choice){"light"->AppCompatDelegate.MODE_NIGHT_NO;"dark"->AppCompatDelegate.MODE_NIGHT_YES;else->AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM}
    fun savedChoice(context:Context)=context.getSharedPreferences("browser-v1",Context.MODE_PRIVATE).getString("theme","system")?:"system"
}

/** Full-screen app surfaces already draw a contrasting background behind system bars. */
@Suppress("DEPRECATION")
internal fun browserSystemBars(window:android.view.Window,view:android.view.View,light:Boolean,media:Boolean=false){
    window.statusBarColor=android.graphics.Color.TRANSPARENT
    window.navigationBarColor=android.graphics.Color.TRANSPARENT
    if(android.os.Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=media
    androidx.core.view.WindowCompat.getInsetsController(window,view).apply{
        isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light
    }
}

@Composable internal fun browserColorScheme():ColorScheme {
    val base=if(isSystemInDarkTheme())darkColorScheme()else lightColorScheme()
    return base.copy(
        primary=colorResource(R.color.browser_primary),
        onPrimary=colorResource(R.color.browser_on_primary),
        primaryContainer=colorResource(R.color.browser_primary_container),
        onPrimaryContainer=colorResource(R.color.browser_on_primary_container),
        secondary=colorResource(R.color.browser_secondary),
        onSecondary=colorResource(R.color.browser_on_secondary),
        secondaryContainer=colorResource(R.color.browser_secondary_container),
        onSecondaryContainer=colorResource(R.color.browser_on_secondary_container),
        tertiary=colorResource(R.color.browser_tertiary),
        onTertiary=colorResource(R.color.browser_on_tertiary),
        tertiaryContainer=colorResource(R.color.browser_tertiary_container),
        onTertiaryContainer=colorResource(R.color.browser_on_tertiary_container),
        background=colorResource(R.color.browser_background),
        onBackground=colorResource(R.color.browser_on_background),
        surface=colorResource(R.color.browser_surface),
        onSurface=colorResource(R.color.browser_on_surface),
        surfaceVariant=colorResource(R.color.browser_surface_variant),
        onSurfaceVariant=colorResource(R.color.browser_on_surface_variant),
        outline=colorResource(R.color.browser_outline),
        outlineVariant=colorResource(R.color.browser_outline_variant),
        error=colorResource(R.color.browser_error),
        onError=colorResource(R.color.browser_on_error),
        errorContainer=colorResource(R.color.browser_error_container),
        onErrorContainer=colorResource(R.color.browser_on_error_container),
        inverseSurface=colorResource(R.color.browser_inverse_surface),
        inverseOnSurface=colorResource(R.color.browser_inverse_on_surface),
        inversePrimary=colorResource(R.color.browser_inverse_primary),
        surfaceDim=colorResource(R.color.browser_surface_dim),
        surfaceBright=colorResource(R.color.browser_surface_bright),
        surfaceContainerLowest=colorResource(R.color.browser_surface_container_lowest),
        surfaceContainerLow=colorResource(R.color.browser_surface_container_low),
        surfaceContainer=colorResource(R.color.browser_surface_container),
        surfaceContainerHigh=colorResource(R.color.browser_surface_container_high),
        surfaceContainerHighest=colorResource(R.color.browser_surface_container_highest),
        surfaceTint=colorResource(R.color.browser_primary)
    )
}

internal val BrowserTypography=Typography(
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    titleMedium=TextStyle(fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
)
