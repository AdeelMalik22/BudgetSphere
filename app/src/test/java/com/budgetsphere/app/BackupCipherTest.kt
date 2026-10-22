package com.budgetsphere.app

import com.budgetsphere.app.core.BackupCipher
import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    @Test
    fun authenticatedBackupRejectsWrongPassphrasesAndTampering() {
        val codec = BackupCipher()
        val content = "private financial records".toByteArray()
        val encrypted = codec.encrypt(content, "a separate backup secret".toCharArray())
        assertArrayEquals(
            content,
            codec.decrypt(encrypted, "a separate backup secret".toCharArray()),
        )
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.decrypt(encrypted, "incorrect backup secret".toCharArray())
        }
        val tampered =
            encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.decrypt(tampered, "a separate backup secret".toCharArray())
        }
        val changedHeader = encrypted.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.decrypt(changedHeader, "a separate backup secret".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decrypt(byteArrayOf(1, 2), "a separate backup secret".toCharArray())
        }
    }
}
