package com.budgetsphere.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.budgetsphere.app.data.AccountEntity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Biometrics(private val activity: FragmentActivity) {
    fun availability(): String? = when(BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a strong fingerprint or face in Android device settings first."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no supported strong biometric sensor. Use your password."
        else -> "Biometrics are unavailable. Use your password or try again later."
    }
    private fun alias(id: String)="budgetsphere.biometric.$id"
    private fun key(id: String): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.getKey(alias(id),null) as? SecretKey
    fun remove(id: String) { KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry(alias(id))} }
    fun enroll(account: AccountEntity, success: (ByteArray,ByteArray)->Unit, failure: (String)->Unit) {
        try {
            availability()?.let { failure(it);return }
            remove(account.id)
            val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias(account.id),KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true).build())
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,generator.generateKey()) }
            prompt("Enable biometric unlock",cipher,{ authenticated->success(authenticated.doFinal(account.id.toByteArray(Charsets.UTF_8)),authenticated.iv) },failure)
        } catch(_: Exception) { failure("Biometric setup failed. Your password still works.") }
    }
    fun unlock(account: AccountEntity, success: (ByteArray)->Unit, failure: (String)->Unit) {
        try {
            availability()?.let { failure(it);return }
            val ciphertext=account.biometricCipher?:error("Not enrolled")
            val iv=account.biometricIv?:error("Not enrolled")
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(account.id)?:error("Key unavailable"),GCMParameterSpec(128,iv)) }
            prompt("Unlock BudgetSphere",cipher,{authenticated->success(authenticated.doFinal(ciphertext))},failure)
        } catch(_: Exception) { failure("Biometric key is unavailable or changed. Use your password, then enable biometrics again.") }
    }
    private fun prompt(title: String,cipher: Cipher,success: (Cipher)->Unit,failure: (String)->Unit) {
        val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try { success(result.cryptoObject?.cipher?:error("Missing authenticated cipher")) } catch(_:Exception) {failure("Biometric verification failed. Use your password.")}
            }
            override fun onAuthenticationError(code: Int,message: CharSequence) { failure(if(code==BiometricPrompt.ERROR_NEGATIVE_BUTTON || code==BiometricPrompt.ERROR_USER_CANCELED) "Biometric unlock canceled. Values remain hidden. You can use your password." else "Biometric unlock unavailable. Use your password.") }
            // A failed scan leaves the prompt open and never grants access.
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Authenticate on this device")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use password").build(),BiometricPrompt.CryptoObject(cipher))
    }
}
