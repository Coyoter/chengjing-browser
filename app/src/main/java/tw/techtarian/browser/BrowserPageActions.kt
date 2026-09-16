package tw.techtarian.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView

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
internal data class PageContextTarget(val link:String?,val image:String?,val isImage:Boolean)

internal class PageContextMenu(
    private val controller:BrowserController,
    private val tab:BrowserTab,
    private val download:(String,String,String)->Unit={url,page,agent->
        val host=controller.context as? MainActivity
        if(host==null)controller.notice="此畫面無法啟動圖片下載"
        else host.imageDownloads.download(url,page,agent,tab)
        Unit
    },
) {
    private val web get()=tab.web
    private var sequence=0
    private var dialog:AlertDialog?=null
    fun install(){
        web.setOnLongClickListener{onLongClick()}
        web.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(view:View){}
            override fun onViewDetachedFromWindow(view:View){sequence++;dialog?.dismiss();dialog=null}
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
            WebView.HitTestResult.IMAGE_TYPE->{show(PageContextTarget(null,extra,true),page,request);true}
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE->{
                // Native WebView resolves the focused node, including images inside frames.
                // Never treat hit.extra (the anchor) as the image to download.
                val handler=Handler(Looper.getMainLooper()){message->
                    if(valid(page,request)){
                        val link=PageActionPolicy.resolve(message.data.getString("url"),page)?:extra
                        val image=PageActionPolicy.resolve(message.data.getString("src"),page)
                        show(PageContextTarget(link,image,true),page,request)
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
    private fun show(target:PageContextTarget,page:String,request:Int){
        if(!valid(page,request))return
        val entries=mutableListOf<Pair<String,()->Unit>>()
        target.link?.let{link->
            PageActionPolicy.shareUrl(link)?.let{safe->entries.add("在新分頁開啟" to {controller.newTab(safe,incognito=tab.incognito)})}
            entries.add("複製連結" to {copy(link,"連結")})
        }
        if(target.isImage){
            entries.add("下載圖片" to {
                val image=target.image
                if(image.isNullOrBlank())controller.notice="無法取得圖片網址，請重新長按圖片"
                else download(image,page,web.settings.userAgentString)
            })
            target.image?.let{image->
                PageActionPolicy.shareUrl(image)?.let{url->entries.add("在新分頁開啟圖片" to {controller.newTab(url,incognito=tab.incognito)})}
                entries.add("複製圖片連結" to {copy(image,"圖片連結")})
            }
        }
        if(entries.isEmpty())return
        dialog?.dismiss()
        val host=controller.context as? Activity
        if(host?.isFinishing==true||host?.isDestroyed==true)return
        dialog=AlertDialog.Builder(controller.context)
            .setItems(entries.map{it.first}.toTypedArray()){_,index->
                if(valid(page,request))entries[index].second()
                else controller.notice="頁面已變更，請重新長按"
            }.create().also{it.show()}
    }
    private fun copy(value:String,label:String){
        (controller.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label,value))
        controller.notice="已複製$label"
    }
}
