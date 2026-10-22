package com.budgetsphere.app.data

import com.budgetsphere.app.core.PasswordHasher
import com.budgetsphere.app.core.PrivacyManager
import com.budgetsphere.app.domain.Validation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@Singleton class AccountRepository @Inject constructor(private val dao: FinanceDao,private val hasher: PasswordHasher,private val privacy: PrivacyManager) {
    private val _session=MutableStateFlow<AccountEntity?>(null)
    val session=_session.asStateFlow()
    private val gate=Mutex()
    private var failures=0
    private var retryAfter=0L
    suspend fun signup(name: String, identifier: String, password: CharArray, confirm: CharArray) = gate.withLock {
        try { withContext(Dispatchers.IO) {
            val normalized=Validation.identifier(identifier)
            require(password.contentEquals(confirm)) { "Passwords do not match" }
            require(dao.account(normalized)==null) { "That username/email is already used on this device" }
            val digest=hasher.create(password)
            val account=AccountEntity(UUID.randomUUID().toString(),Validation.name(name),normalized,digest.salt,digest.hash,digest.iterations)
            dao.insertAccount(account); privacy.bind(null); _session.value=account
        } } finally { password.fill('\u0000'); confirm.fill('\u0000') }
    }
    suspend fun login(identifier: String, password: CharArray) = gate.withLock {
        try { withContext(Dispatchers.IO) {
            checkThrottle()
            val account=dao.account(Validation.identifier(identifier))
            val valid= if(account!=null) hasher.verify(password,account.salt,account.hash,account.iterations) else {
                hasher.derive(password,ByteArray(32),600000); false
            }
            if(!valid) { failed(); error("Incorrect username/email or password") }
            failures=0; retryAfter=0; privacy.bind(null); _session.value=account
        } } finally { password.fill('\u0000') }
    }
    fun requireAccount(): AccountEntity = _session.value ?: error("Please log in again")
    suspend fun verify(password: CharArray): Boolean = gate.withLock {
        try { withContext(Dispatchers.IO) {
            checkThrottle()
            val account=dao.accountById(requireAccount().id) ?: error("Account no longer exists")
            hasher.verify(password,account.salt,account.hash,account.iterations).also { if(it) {failures=0;retryAfter=0} else failed() }
        } } finally { password.fill('\u0000') }
    }
    suspend fun changePassword(old: CharArray, next: CharArray, confirm: CharArray) {
        try {
            require(verify(old)) { "Incorrect current password" }
            require(next.contentEquals(confirm)) { "Passwords do not match" }
            withContext(Dispatchers.IO) {
                val account=requireAccount(); val digest=hasher.create(next)
                val updated=account.copy(salt=digest.salt,hash=digest.hash,iterations=digest.iterations,biometricCipher=null,biometricIv=null)
                dao.updateAccount(updated); _session.value=updated
            }
        } finally { old.fill('\u0000');next.fill('\u0000');confirm.fill('\u0000') }
    }
    suspend fun biometricAccount(identifier: String)=withContext(Dispatchers.IO) { dao.account(Validation.identifier(identifier)) }
    suspend fun enableBiometric(accountId: String, ciphertext: ByteArray, iv: ByteArray) = withContext(Dispatchers.IO) {
        val account=requireAccount(); require(account.id==accountId)
        val updated=account.copy(biometricCipher=ciphertext,biometricIv=iv); dao.updateAccount(updated);_session.value=updated
    }
    suspend fun disableBiometric()=withContext(Dispatchers.IO) {
        val updated=requireAccount().copy(biometricCipher=null,biometricIv=null);dao.updateAccount(updated);_session.value=updated
    }
    suspend fun biometricLogin(account: AccountEntity, decrypted: ByteArray) {
        require(java.security.MessageDigest.isEqual(decrypted,account.id.toByteArray(Charsets.UTF_8))) { "Biometric unlock failed. Use your password." }
        withContext(Dispatchers.IO) {
            val current=dao.accountById(account.id)?:error("Account no longer exists")
            require(current.biometricCipher?.contentEquals(account.biometricCipher?:byteArrayOf())==true) { "Biometric setup changed. Use your password." }
            privacy.bind(null);_session.value=current
        }
        decrypted.fill(0)
    }
    fun logout() { privacy.bind(null);_session.value=null }
    private fun checkThrottle() { require(System.nanoTime()>=retryAfter) { "Too many attempts. Wait 30 seconds and try again." } }
    private fun failed() { failures++; if(failures>=5) retryAfter=System.nanoTime()+30_000_000_000L }
}
