package com.budgetsphere.app
import com.budgetsphere.app.core.PrivacyManager
import com.budgetsphere.app.domain.Scope
import org.junit.Test
import org.junit.Assert.*
class PrivacyManagerTest {
    @Test fun grantsCannotSurviveScopeSwitchHideOrFailedAuthentication() {
        val privacy=PrivacyManager(); val personal=Scope("account","personal");val friend=Scope("account","friend")
        privacy.bind(personal);val challenge=privacy.challenge()
        assertFalse(privacy.revealAfterVerification(challenge,personal,false));assertTrue(privacy.hidden.value)
        privacy.bind(friend)
        assertFalse(privacy.revealAfterVerification(challenge,personal,true));assertTrue(privacy.hidden.value)
        val next=privacy.challenge();assertTrue(privacy.revealAfterVerification(next,friend,true));assertFalse(privacy.hidden.value)
        privacy.hide();assertTrue(privacy.hidden.value);assertFalse(privacy.revealAfterVerification(next,friend,true))
        privacy.bind(null);assertFalse(privacy.revealAfterVerification(privacy.challenge(),friend,true))
    }
}
