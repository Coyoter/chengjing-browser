package tw.techtarian.browser

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppLanguagePolicyTest {
    @Test fun allSeventeenRequestedLanguagesHaveUniqueSupportedChoices(){
        assertEquals(17,AppLanguagePolicy.languages.size)
        assertEquals(17,AppLanguagePolicy.languages.map{it.tag}.toSet().size)
        AppLanguagePolicy.languages.forEach{assertTrue(AppLanguagePolicy.validChoice(it.tag));assertTrue(it.autonym.isNotBlank())}
        assertTrue(AppLanguagePolicy.validChoice("system"));assertFalse(AppLanguagePolicy.validChoice("el"))
    }
    @Test fun chineseScriptAndRegionAreMatchedWithoutMixingWritingSystems(){
        listOf("zh-TW","zh-HK","zh-MO","zh-Hant","zh-Hant-CN").forEach{assertEquals(it,"zh-TW",AppLanguagePolicy.match(Locale.forLanguageTag(it)))}
        listOf("zh","zh-CN","zh-SG","zh-Hans","zh-Hans-TW").forEach{assertEquals(it,"zh-CN",AppLanguagePolicy.match(Locale.forLanguageTag(it)))}
    }
    @Test fun regionalPhoneLanguagesUseTheSupportedLanguageAndUnsupportedPhonesUseEnglish(){
        mapOf("en-GB" to "en","fr-CA" to "fr","pt-BR" to "pt","pt-PT" to "pt","ar-EG" to "ar","hi-IN" to "hi","id-ID" to "id","ur-PK" to "ur").forEach{(input,expected)->assertEquals(expected,AppLanguagePolicy.match(Locale.forLanguageTag(input)))}
        assertEquals("en",AppLanguagePolicy.resolve(listOf(Locale.forLanguageTag("el-GR"))))
        assertEquals("en",AppLanguagePolicy.resolve(emptyList()))
        assertEquals("ko",AppLanguagePolicy.resolve(listOf(Locale.forLanguageTag("el-GR"),Locale.KOREAN)))
    }
    @Test fun onlyArabicAndUrduUseRightToLeftLayout(){
        assertTrue(AppLanguagePolicy.isRtl("ar"));assertTrue(AppLanguagePolicy.isRtl("ur"))
        AppLanguagePolicy.languages.filter{it.tag !in setOf("ar","ur")}.forEach{assertFalse(AppLanguagePolicy.isRtl(it.tag))}
    }
}
