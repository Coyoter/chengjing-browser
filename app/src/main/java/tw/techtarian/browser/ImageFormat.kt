package tw.techtarian.browser

internal enum class ImageFormat(val mime:String,val extension:String) {
    PNG("image/png","png"),JPEG("image/jpeg","jpg"),GIF("image/gif","gif"),
    WEBP("image/webp","webp"),BMP("image/bmp","bmp"),ICO("image/x-icon","ico"),
    AVIF("image/avif","avif"),HEIC("image/heic","heic"),HEIF("image/heif","heif");
    companion object {
        fun sniff(bytes:ByteArray):ImageFormat? {
            fun at(offset:Int,text:String)=bytes.size>=offset+text.length&&text.indices.all{bytes[offset+it].toInt() and 255==text[it].code}
            fun starts(vararg values:Int)=bytes.size>=values.size&&values.indices.all{bytes[it].toInt() and 255==values[it]}
            return when {
                starts(137,80,78,71,13,10,26,10)->PNG
                starts(255,216,255)->JPEG
                at(0,"GIF87a")||at(0,"GIF89a")->GIF
                at(0,"RIFF")&&at(8,"WEBP")->WEBP
                at(0,"BM")->BMP
                starts(0,0,1,0)->ICO
                at(4,"ftyp")-> {
                    val brands=(8 until minOf(bytes.size,64)-3 step 4).map{String(bytes,it,4,Charsets.US_ASCII)}
                    when {brands.any{it in setOf("avif","avis")}->AVIF
                        brands.any{it in setOf("heic","heix","hevc","hevx")}->HEIC
                        brands.any{it in setOf("mif1","msf1")}->HEIF
                        else->null}
                }
                else->null
            }
        }
        fun forExtension(extension:String)=entries.firstOrNull{it.extension==extension.lowercase()}
    }
    fun filename(suggestion:String,suffix:String):String {
        val leaf=suggestion.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#')
        val dot=leaf.lastIndexOf('.')
        val stem=if(dot>0&&leaf.length-dot<=12)leaf.substring(0,dot)else leaf
        return PageActionPolicy.safeFilename("${stem.ifBlank{"image"}}.$extension",suffix)
    }
}
