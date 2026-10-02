package tw.techtarian.browser

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

internal object PageSharing {
    fun chooser(rawUrl:String?,title:String):Intent? {
        val url=PageActionPolicy.shareUrl(rawUrl)?:return null
        val label=title.ifBlank{url}.take(500)
        val send=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_TEXT,url)
            putExtra(Intent.EXTRA_SUBJECT,label)
            putExtra(Intent.EXTRA_TITLE,label)
        }
        // Do not pick a target package, query installed apps, or copy into the clipboard.
        return Intent.createChooser(send,null)
    }
}

internal fun BrowserController.shareCurrentPage():Boolean {
    val page=active
    val chooser=PageSharing.chooser(page?.url,page?.title.orEmpty())
    if(chooser==null){notice=bt(R.string.msg_1cba5bfa6f7a);return false}
    return try {
        if(context !is Activity)chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        sheet=""
        true // Opening the chooser does not mean the user completed a share.
    }catch(_:android.content.ActivityNotFoundException){notice=bt(R.string.msg_9e2276893fae);false}
    catch(_:SecurityException){notice=bt(R.string.msg_5e1b8f022826);false}
}

/** The link destination and image source are different values for <a><img></a>. */
internal data class PageContextTarget(val link:String?,val image:String?,val isImage:Boolean,val title:String="",val width:Int=0,val height:Int=0)

internal class PageContextMenu(
    private val controller:BrowserController,
    private val tab:BrowserTab,
    private val download:(String,String,String)->Unit={url,page,agent->
        val host=controller.context as? MainActivity
        if(host==null)controller.notice=bt(R.string.msg_beed38829f24)
        else controller.downloadFor(tab,url,page,agent)
        Unit
    },
) {
    private val web get()=tab.web
    private var sequence=0
    private var dialog:BrowserPrompt?=null
    fun install(){
        web.setOnLongClickListener{onLongClick()}
        web.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(view:View){}
            override fun onViewDetachedFromWindow(view:View){sequence++;controller.prompts.cancel(dialog);dialog=null}
        })
    }
    private fun valid(page:String,request:Int)=request==sequence&&tab in controller.tabs&&controller.activeId==tab.id&&tab.url==page&&!controller.eye&&!web.selecting&&web.isAttachedToWindow

    fun onLongClick():Boolean {
        if(controller.eye||web.selecting||controller.activeId!=tab.id)return false
        val hit=web.hitTestResult
        val page=tab.url
        val request=++sequence
        val extra=PageActionPolicy.resolve(hit.extra,page)
        return when(hit.type){
            WebView.HitTestResult.IMAGE_TYPE->{resolveImage(PageContextTarget(null,extra,true),page,request);true}
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE->{
                // Native WebView resolves the focused node, including images inside frames.
                // Never treat hit.extra (the anchor) as the image to download.
                val handler=Handler(Looper.getMainLooper()){message->
                    if(valid(page,request)){
                        val link=PageActionPolicy.resolve(message.data.getString("url"),page)?:extra
                        val image=PageActionPolicy.resolve(message.data.getString("src"),page)
                        resolveImage(PageContextTarget(link,image,true),page,request)
                    }
                    true
                }
                web.requestFocusNodeHref(handler.obtainMessage())
                true
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE->{
                if(extra==null)false else {show(PageContextTarget(extra,null,false),page,request);true}
            }
            else->false // Preserve selection and the native text/edit-field menu.
        }
    }
    private fun resolveImage(target:PageContextTarget,page:String,request:Int){
        val host=controller.context as? MainActivity
        if(host==null||target.image==null){show(target,page,request);return}
        host.lifecycleScope.launch{
            val source=PageImageReader.resolve(tab,target.image,page)
            if(valid(page,request))show(target.copy(image=source.url,title=source.title,width=source.width,height=source.height),page,request)
        }
    }
    private fun show(target:PageContextTarget,page:String,request:Int){
        if(!valid(page,request))return
        val entries=mutableListOf<Pair<String,()->Unit>>()
        target.link?.let{link->
            PageActionPolicy.shareUrl(link)?.let{safe->entries.add(bt(R.string.msg_b7d4526756b3) to {controller.newTab(safe)})}
            entries.add(bt(R.string.msg_d55104374e08) to {copy(link,bt(R.string.msg_e9b3cec96921))})
        }
        if(target.isImage){
            val source=target.image?.let{PageImageSource(it,page,target.title,target.width,target.height)}
            fun perform(action:ImageAction){if(source!=null)(controller.context as? MainActivity)?.imageActions?.perform(tab,source,action)}
            if(source!=null){
                entries.add(bt(R.string.msg_e4a47c1dba73) to {perform(ImageAction.NEW_TAB)})
                entries.add(bt(R.string.msg_3b0e45edcfb7) to {perform(ImageAction.PREVIEW)})
                entries.add(bt(R.string.msg_720b7e9d72fa) to {perform(ImageAction.COPY)})
            }
            entries.add(bt(R.string.msg_b3436480e1ad) to {
                val image=target.image
                if(image.isNullOrBlank())controller.notice=bt(R.string.msg_75136c12a08a)
                else download(image,page,web.settings.userAgentString)
            })
            target.image?.let{image->
                entries.add(bt(R.string.msg_750f7a0175cc) to {perform(ImageAction.SHARE)})
                PageActionPolicy.shareUrl(image)?.let{url->entries.add(bt(R.string.msg_3c028eeb1e92) to {copy(url,bt(R.string.msg_559552ccc661))})}
            }
        }
        if(entries.isEmpty())return
        controller.prompts.cancel(dialog)
        val host=controller.context as? Activity
        if(host?.isFinishing==true||host?.isDestroyed==true)return
        val icons=mapOf(bt(R.string.msg_b7d4526756b3) to Icons.Outlined.OpenInNew,bt(R.string.msg_d55104374e08) to Icons.Outlined.Link,
            bt(R.string.msg_e4a47c1dba73) to Icons.Outlined.OpenInNew,bt(R.string.msg_3b0e45edcfb7) to Icons.Outlined.ZoomIn,
            bt(R.string.msg_720b7e9d72fa) to Icons.Outlined.ContentCopy,bt(R.string.msg_b3436480e1ad) to Icons.Outlined.Download,
            bt(R.string.msg_750f7a0175cc) to Icons.Outlined.Share,bt(R.string.msg_3c028eeb1e92) to Icons.Outlined.Link)
        dialog=BrowserPrompt.Menu(
            if(target.isImage)target.title.ifBlank{bt(R.string.msg_b210350f38fc)}.take(120)else bt(R.string.msg_e9b3cec96921),
            android.net.Uri.parse(if(target.isImage)page else target.link?:page).host.orEmpty(),
            entries.map{(label,action)->BrowserMenuAction(label,icons.getValue(label),action)},tab.id,
            {valid(page,request)}
        ).also{controller.prompts.show(it)}
    }
    private fun copy(value:String,label:String){
        (controller.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label,value))
        controller.notice=bt(R.string.msg_17d52a476d32 ,label)
    }
}
