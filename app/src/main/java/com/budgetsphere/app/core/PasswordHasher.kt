package com.budgetsphere.app.core

import com.budgetsphere.app.domain.Validation
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordDigest(val salt: ByteArray, val hash: ByteArray, val iterations: Int)
class PasswordHasher {
    fun create(password: CharArray): PasswordDigest {
        require(password.size in 12..256) { "Use a password with 12–256 characters" }
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        return PasswordDigest(salt, derive(password, salt, 600000), 600000)
    }
    fun verify(password: CharArray, salt: ByteArray, expected: ByteArray, iterations: Int): Boolean {
        if(password.size !in 1..256 || salt.size != 32 || expected.size != 32 || iterations !in 600000..2000000) return false
        val actual = derive(password, salt, iterations)
        return try { MessageDigest.isEqual(expected, actual) } finally { actual.fill(0) }
    }
    fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
