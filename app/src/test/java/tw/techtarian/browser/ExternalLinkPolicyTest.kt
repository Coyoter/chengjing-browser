package tw.techtarian.browser

import org.junit.Assert.*
import org.junit.Test

class ExternalLinkPolicyTest {
    @Test fun onlyTheClickedNavigationAndItsBoundedRedirectsAreAuthorized(){
        var now=100L;val gate=ExternalLinkGesture{now}
        assertFalse(gate.allow(true,false,false))
        assertFalse(gate.allow(true,false,true))
        assertTrue(gate.allow(true,true,false))
        assertFalse(gate.allow(true,false,false)) // a JavaScript timer is not a redirect
        assertFalse(gate.allow(false,true,true)) // a frame is never a launch surface
        assertTrue(gate.allow(true,false,true))
        now+=10_001;assertFalse(gate.allow(true,false,true))
    }
    @Test fun finishingOrNativeNavigationRevokesAuthorization(){
        val gate=ExternalLinkGesture{100}
        gate.allow(true,true,false);gate.reset()
        assertFalse(gate.allow(true,false,true))
    }
    @Test fun clickedPopupHasOneShortLivedInitialNavigation(){
        var now=100L;val gate=ExternalLinkGesture{now}
        gate.popupFromClick()
        assertTrue(gate.allow(true,false,false))
        assertFalse(gate.allow(true,false,false))
        gate.popupFromClick();now+=3_001
        assertFalse(gate.allow(true,false,false))
    }
    @Test fun browserInternalSchemesCannotBecomeAppLaunches(){
        listOf("file","content","javascript","data","blob","about","intent","android-app","view-source","filesystem").forEach{assertFalse(it,ExternalLinkPolicy.permittedScheme(it))}
        listOf("vnd.youtube","youtube","mailto","tel","geo","market","https","line","chengjing-test").forEach{assertTrue(it,ExternalLinkPolicy.permittedScheme(it))}
        assertTrue(ExternalLinkPolicy.isWeb("HTTPS"));assertFalse(ExternalLinkPolicy.isWeb("intent"))
        assertFalse(ExternalLinkPolicy.validInput("youtube://watch\nmalformed"))
        assertFalse(ExternalLinkPolicy.validInput("x".repeat(16_385)))
    }
}
