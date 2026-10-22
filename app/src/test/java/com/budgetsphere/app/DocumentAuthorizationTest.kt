package com.budgetsphere.app
import com.budgetsphere.app.domain.*
import org.junit.Test
import org.junit.Assert.*
class DocumentAuthorizationTest {
    @Test fun documentGrantsSurviveBackgroundHideButNeverScopeChangesOrReuse() {
        val authorization=DocumentAuthorization();val scope=Scope("owner","personal")
        assertThrows(IllegalArgumentException::class.java){authorization.authorize(scope,"CSV",false)}
        authorization.authorize(scope,"CSV",true)
        assertTrue(authorization.consume(scope,"CSV"))
        assertFalse(authorization.consume(scope,"CSV"))
        authorization.authorize(scope,"Backup",true)
        assertFalse(authorization.consume(Scope("owner","friend"),"Backup"))
        assertFalse(authorization.consume(scope,"Backup"))
        authorization.authorize(scope,"Backup",true)
        assertFalse(authorization.consume(null,"Backup"))
    }
}
