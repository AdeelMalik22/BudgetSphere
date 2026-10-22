package com.budgetsphere.app
import com.budgetsphere.app.core.PasswordHasher
import org.junit.Assert.*
import org.junit.Test
class PasswordHasherTest {
    @Test fun saltedVerifiersRejectWrongPasswordsAndMalformedParameters() {
        val hasher=PasswordHasher()
        val first=hasher.create("a secure local password".toCharArray())
        val second=hasher.create("a secure local password".toCharArray())
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.hash.contentEquals(second.hash))
        assertTrue(hasher.verify("a secure local password".toCharArray(),first.salt,first.hash,first.iterations))
        assertFalse(hasher.verify("wrong password".toCharArray(),first.salt,first.hash,first.iterations))
        assertFalse(hasher.verify("a secure local password".toCharArray(),first.salt,first.hash,1))
    }
}
