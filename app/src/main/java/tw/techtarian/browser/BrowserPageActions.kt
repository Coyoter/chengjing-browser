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
    if(chooser==null){notice="先開啟網頁，再分享連結";return false}
    return try {
        if(context !is Activity)chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        sheet=""
        true // Opening the chooser does not mean the user completed a share.
    }catch(_:android.content.ActivityNotFoundException){notice="此裝置無法開啟系統分享面板";false}
    catch(_:SecurityException){notice="系統暫時無法分享這個頁面";false}
}

/** The link destination and image source are different values for <a><img></a>. */
internal data class PageContextTarget(val link:String?,val image:String?,val isImage:Boolean,val title:String="",val width:Int=0,val height:Int=0)

internal class PageContextMenu(
    private val controller:BrowserController,
    private val tab:BrowserTab,
    private val download:(String,String,String)->Unit={url,page,agent->
        val host=controller.context as? MainActivity
        if(host==null)controller.notice="此畫面無法啟動圖片下載"
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
            PageActionPolicy.shareUrl(link)?.let{safe->entries.add("在新分頁開啟" to {controller.newTab(safe)})}
            entries.add("複製連結" to {copy(link,"連結")})
        }
        if(target.isImage){
            val source=target.image?.let{PageImageSource(it,page,target.title,target.width,target.height)}
            fun perform(action:ImageAction){if(source!=null)(controller.context as? MainActivity)?.imageActions?.perform(tab,source,action)}
            if(source!=null){
                entries.add("在新分頁開啟圖片" to {perform(ImageAction.NEW_TAB)})
                entries.add("預覽圖片" to {perform(ImageAction.PREVIEW)})
                entries.add("複製圖片" to {perform(ImageAction.COPY)})
            }
            entries.add("下載圖片" to {
                val image=target.image
                if(image.isNullOrBlank())controller.notice="無法取得圖片網址，請重新長按圖片"
                else download(image,page,web.settings.userAgentString)
            })
            target.image?.let{image->
                entries.add("分享圖片" to {perform(ImageAction.SHARE)})
                PageActionPolicy.shareUrl(image)?.let{url->entries.add("複製圖片連結" to {copy(url,"圖片連結")})}
            }
        }
        if(entries.isEmpty())return
        controller.prompts.cancel(dialog)
        val host=controller.context as? Activity
        if(host?.isFinishing==true||host?.isDestroyed==true)return
        val icons=mapOf("在新分頁開啟" to Icons.Outlined.OpenInNew,"複製連結" to Icons.Outlined.Link,
            "在新分頁開啟圖片" to Icons.Outlined.OpenInNew,"預覽圖片" to Icons.Outlined.ZoomIn,
            "複製圖片" to Icons.Outlined.ContentCopy,"下載圖片" to Icons.Outlined.Download,
            "分享圖片" to Icons.Outlined.Share,"複製圖片連結" to Icons.Outlined.Link)
        dialog=BrowserPrompt.Menu(
            if(target.isImage)target.title.ifBlank{"圖片"}.take(120)else "連結",
            android.net.Uri.parse(if(target.isImage)page else target.link?:page).host.orEmpty(),
            entries.map{(label,action)->BrowserMenuAction(label,icons.getValue(label),action)},tab.id,
            {valid(page,request)}
        ).also{controller.prompts.show(it)}
    }
    private fun copy(value:String,label:String){
        (controller.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label,value))
        controller.notice="已複製$label"
    }
}
