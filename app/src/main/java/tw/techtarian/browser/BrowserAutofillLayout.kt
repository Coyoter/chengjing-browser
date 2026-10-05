package tw.techtarian.browser

import android.content.Context
import android.view.ViewGroup
import android.view.ViewStructure
import android.webkit.WebView
import android.widget.FrameLayout

/** Compose exposes its semantic fields, but omits AndroidView's native virtual form tree. */
internal class BrowserAutofillLayout(context:Context,private val activeWeb:()->WebView?):FrameLayout(context){
    init{importantForAutofill=IMPORTANT_FOR_AUTOFILL_YES}
    override fun onProvideAutofillVirtualStructure(structure:ViewStructure,flags:Int){
        super.onProvideAutofillVirtualStructure(structure,flags)
        val web=activeWeb()?:return
        if(!web.isAttachedToWindow||!web.isShown||web.importantForAutofill==IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)return
        val child=structure.newChild(structure.addChildCount(1))
        // Keep WebView's own origin, real AutofillIds, sensitivity and complete form metadata.
        // Android delivers values back to that same physical WebView in the normal view tree.
        web.dispatchProvideAutofillStructure(child,flags)
        val origin=IntArray(2);val position=IntArray(2)
        getLocationOnScreen(origin);web.getLocationOnScreen(position)
        child.setDimens(position[0]-origin[0],position[1]-origin[1],web.scrollX,web.scrollY,web.width,web.height)
    }
    companion object {
        fun install(activity:MainActivity){
            val content=activity.findViewById<ViewGroup>(android.R.id.content)
            val compose=content.getChildAt(0)?:return
            content.removeView(compose)
            val host=BrowserAutofillLayout(activity){activity.controller.takeIf{it.sheet.isEmpty()&&!it.eye}?.active?.web}
            host.addView(compose,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
            content.addView(host,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}
