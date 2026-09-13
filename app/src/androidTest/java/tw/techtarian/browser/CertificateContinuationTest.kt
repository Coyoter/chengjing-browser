package tw.techtarian.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CertificateContinuationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val origin="https://10.0.2.2:18743/"
    private val site="https://10.0.2.2/"
    private val resource="https://127.0.0.1:18743/"
    @Before fun fixtureIsExplicit(){
        org.junit.Assume.assumeTrue("TLS fixture test is opt-in",InstrumentationRegistry.getArguments().getString("tlsFixture")=="true")
        org.junit.Assume.assumeTrue(ui.activity.packageName.endsWith(".qa"))
        main{listOf(origin,resource).forEach{c.store.setCertificateException(it,false)};c.tabs.forEach{it.web.clearSslPreferences()}}
    }
    @After fun resetFixtureExceptions(){if(ui.activity.packageName.endsWith(".qa"))main{listOf(origin,resource).forEach{c.store.setCertificateException(it,false)};c.tabs.forEach{it.web.clearSslPreferences()}}}
    private val c get()=ui.activity.controller
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun eval(script:String):String{val done=CountDownLatch(1);var result="";main{c.active!!.web.evaluateJavascript(script){result=it;done.countDown()}};check(done.await(8,TimeUnit.SECONDS));return result}
    private fun until(check:()->Boolean){val end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){if(runCatching(check).getOrDefault(false))return;Thread.sleep(150)};assertTrue("Condition timed out: error=${c.active?.error}; warning=${c.active?.certificateWarning}; pending=${c.active?.pendingUrl}",check())}
    private fun open(url:String){main{c.newTab(url)};until{c.active!!.progress==100}}
    @Test fun explicitSiteExceptionPersistsAndCanBeRevoked(){
        open(origin+"main")
        until{c.active!!.error.isNotEmpty()}
        assertNotEquals("true",eval("!!document.querySelector('#tls-main')"))
        assertTrue("URL=${c.active!!.url}; pending=${c.active!!.pendingUrl}; error=${c.active!!.error}; tree="+ui.onRoot().printToString(),c.active!!.certificateWarning.isNotEmpty())
        ui.onNodeWithContentDescription("憑證異常，點擊查看").performClick()
        ui.onNodeWithTag("certificate-exception:$site").performScrollTo().performClick()
        ui.onNodeWithText("允許這個網站的憑證例外？").assertExists()
        ui.onNodeWithText("我了解，允許此網站").performClick()
        until{eval("!!document.querySelector('#tls-main')")=="true"&&c.active!!.error.isEmpty()}
        ui.onNodeWithContentDescription("憑證例外已開啟，點擊查看").assertExists()
        assertTrue(BrowserStore(ui.activity).certificateException(origin+"different-path"))
        assertFalse(c.store.certificateException(resource))
        assertTrue(c.store.certificateException("https://10.0.2.2:18744/"))
        ui.activityRule.scenario.recreate()
        until{eval("!!document.querySelector('#tls-main')")=="true"&&c.active!!.error.isEmpty()}
        ui.onNodeWithContentDescription("憑證例外已開啟，點擊查看").performClick()
        ui.onNodeWithTag("certificate-exception:$site").performScrollTo().assertIsOn().performClick()
        until{c.active!!.error.isNotEmpty()}
        assertFalse(BrowserStore(ui.activity).certificateException(origin))
        ui.onNodeWithText("暫時連不上這個網站").assertIsDisplayed()
        open(origin+"main?after-revoke=1")
        until{c.active!!.error.isNotEmpty()}
        assertNotEquals("true",eval("!!document.querySelector('#tls-main')"))
    }
    @Test fun siteExceptionCoversPortsConsistentlyWithWebView(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("tlsSecondPort")=="true")
        main{c.store.setCertificateException(origin,true)}
        open(origin+"main")
        until{eval("!!document.querySelector('#tls-main')")=="true"}
        eval("const img=document.createElement('img');img.id='certificate-image';img.src='https://10.0.2.2:18744/pixel';document.body.append(img)")
        until{eval("document.querySelector('#certificate-image')?.complete")=="true"}
        assertEquals("The user explicitly allows the same HTTPS host across ports","1",eval("document.querySelector('#certificate-image').naturalWidth"))
    }
    @Test fun untrustedResourceIsBlockedWithoutReplacingMainPage(){
        open("https://example.com/")
        until{eval("document.querySelector('h1')?.textContent")=="\"Example Domain\""}
        eval("const img=document.createElement('img');img.id='certificate-image';img.src='${resource}pixel';document.body.append(img)")
        until{c.active!!.certificateWarning.isNotEmpty()&&eval("document.querySelector('#certificate-image')?.complete")=="true"}
        assertEquals("0",eval("document.querySelector('#certificate-image').naturalWidth"))
        assertEquals("",c.active!!.error)
        assertEquals("\"Example Domain\"",eval("document.querySelector('h1').textContent"))
        assertTrue(CertificateWarnings.session.originsFor("https://example.com/").contains(CertificateExceptions.site(resource)))
        // Allowing the page's own origin must not authorize a different resource origin.
        main{c.store.setCertificateException("https://example.com/",true)}
        try{
            assertFalse(c.store.certificateException(resource))
            eval("document.querySelector('#certificate-image').src='${resource}pixel?again=1'")
            until{eval("document.querySelector('#certificate-image')?.complete")=="true"}
            assertEquals("0",eval("document.querySelector('#certificate-image').naturalWidth"))
        }finally{main{c.store.setCertificateException("https://example.com/",false)}}
    }
}
