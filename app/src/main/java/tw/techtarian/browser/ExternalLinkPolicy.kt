package tw.techtarian.browser

/** Only a user click (or its short, server-side redirect chain) may leave the browser. */
internal class ExternalLinkGesture(private val clock:()->Long) {
    private var redirectUntil=0L
    private var popupUntil=0L
    fun popupFromClick(){popupUntil=clock()+3_000}
    fun reset(){redirectUntil=0;popupUntil=0}
    fun allow(mainFrame:Boolean,hasGesture:Boolean,isRedirect:Boolean):Boolean {
        if(!mainFrame)return false
        val now=clock()
        val popup=popupUntil>0&&now<=popupUntil
        popupUntil=0
        if(hasGesture||popup){redirectUntil=now+10_000;return true}
        // A new, non-user navigation is not part of the earlier clicked chain.
        if(!isRedirect){redirectUntil=0;return false}
        return isRedirect&&redirectUntil>0&&now<=redirectUntil
    }
}

internal object ExternalLinkPolicy {
    private val schemePattern=Regex("[a-z][a-z0-9+.-]{0,63}")
    private val blocked=setOf("about","javascript","data","blob","file","content","android.resource","chrome","chrome-native","chrome-search","view-source","filesystem","intent","android-app")
    fun permittedScheme(scheme:String?)=scheme!=null&&schemePattern.matches(scheme.lowercase())&&scheme.lowercase() !in blocked
    fun isWeb(scheme:String?)=scheme.equals("https",true)||scheme.equals("http",true)
    fun validInput(raw:String)=raw.length in 1..16_384&&raw.none{it.code<32||it.code==127}
}
