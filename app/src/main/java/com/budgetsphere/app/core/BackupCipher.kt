package com.budgetsphere.app.core

import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, bounded PBKDF2-SHA256 + AES-256-GCM envelope. Header is authenticated. */
class BackupCipher {
    companion object {
        const val MAX_BYTES = 80 * 1024 * 1024
        private const val ITERATIONS = 600000
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        try {
            require(passphrase.size in 12..256) { "Use a backup passphrase with 12–256 characters" }
            require(plaintext.size <= MAX_BYTES)
            val random = SecureRandom()
            val salt = ByteArray(32).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val header =
                ByteArrayOutputStream()
                    .also {
                        DataOutputStream(it).use { out ->
                            out.writeBytes("BSPH")
                            out.writeInt(1)
                            out.writeInt(ITERATIONS)
                            out.write(salt)
                            out.write(nonce)
                        }
                    }
                    .toByteArray()
            val key = PasswordHasher().derive(passphrase, salt, ITERATIONS)
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, nonce),
                )
                cipher.updateAAD(header)
                header + cipher.doFinal(plaintext)
            } finally {
                key.fill(0)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun decrypt(encrypted: ByteArray, passphrase: CharArray): ByteArray {
        try {
            require(passphrase.size in 12..256)
            require(encrypted.size in 72..(MAX_BYTES + 72)) { "Invalid or oversized backup" }
            val input = DataInputStream(ByteArrayInputStream(encrypted))
            val magic = ByteArray(4).also(input::readFully)
            require(String(magic, Charsets.US_ASCII) == "BSPH" && input.readInt() == 1) {
                "Unsupported backup format"
            }
            val iterations = input.readInt()
            require(iterations == ITERATIONS) { "Unsupported backup key settings" }
            val salt = ByteArray(32).also(input::readFully)
            val nonce = ByteArray(12).also(input::readFully)
            val header = encrypted.copyOfRange(0, 56)
            val key = PasswordHasher().derive(passphrase, salt, iterations)
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, nonce),
                )
                cipher.updateAAD(header)
                cipher.doFinal(encrypted, 56, encrypted.size - 56)
            } finally {
                key.fill(0)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }
}
