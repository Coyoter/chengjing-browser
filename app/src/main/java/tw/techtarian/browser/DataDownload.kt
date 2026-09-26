package tw.techtarian.browser

import java.io.InputStream
import java.util.Base64

/** Decodes data URLs incrementally, without duplicating the complete payload in memory. */
internal class DataDownload(private val url:String) {
    private val comma=url.indexOf(',').also{require(it in 5..1024){"內嵌檔案格式不正確"}}
    private val header=url.substring(5,comma)
    private val base64=header.split(';').any{it.equals("base64",true)}
    val mime=DownloadFormat.mime(header.substringBefore(';'))?:"text/plain"
    init{require(url.startsWith("data:")){"內嵌檔案網址不正確"};require(url.length<=48*1024*1024){"內嵌資料網址過大，請使用網站的檔案下載連結"}}
    fun open():InputStream {
        val input=object:InputStream(){
            var position=comma+1;var pending=ByteArray(0);var pendingIndex=0
            override fun read():Int {
                while(true){
                    val value=when{
                        pendingIndex<pending.size->pending[pendingIndex++].toInt() and 255
                        position>=url.length->return -1
                        url[position]=='%'->{
                            require(position+2<url.length){"內嵌檔案編碼不完整"}
                            val byte=url.substring(position+1,position+3).toIntOrNull(16)?:error("內嵌檔案編碼不正確")
                            position+=3;byte
                        }
                        url[position].code<128->url[position++].code
                        else->{val point=url.codePointAt(position);position+=Character.charCount(point);pending=String(Character.toChars(point)).toByteArray(Charsets.UTF_8);pendingIndex=1;pending[0].toInt() and 255}
                    }
                    if(!base64||(value!=9&&value!=10&&value!=13&&value!=32))return value
                }
            }
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                if(length==0)return 0
                var n=0;while(n<length){val value=read();if(value<0)break;buffer[offset+n++]=value.toByte()};return if(n==0)-1 else n
            }
        }
        return if(base64)Base64.getDecoder().wrap(input)else input
    }
}
