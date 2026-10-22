package com.budgetsphere.app.data

import com.budgetsphere.app.core.PasswordDerivation
import com.budgetsphere.app.core.PrivacyManager
import com.budgetsphere.app.domain.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AccountRepository
@Inject
constructor(
    private val dao: FinanceDao,
    private val hasher: PasswordDerivation,
    private val privacy: PrivacyManager,
) : AccountStore {
    private val _session = MutableStateFlow<LocalAccount?>(null)
    override val session = _session.asStateFlow()
    private val gate = Mutex()
    private var failures = 0
    private var retryAfter: Long? = null
    private val sessionLock = Any()
    private var sessionGeneration = 0L

    private fun generation(): Long = synchronized(sessionLock) { sessionGeneration }

    private fun checkSession(expected: Long, account: String) =
        synchronized(sessionLock) {
            require(sessionGeneration == expected && _session.value?.id == account) {
                "Session changed. Please log in again."
            }
        }

    private suspend fun publish(expected: Long, account: AccountEntity, newSession: Boolean) {
        currentCoroutineContext().ensureActive()
        synchronized(sessionLock) {
            require(sessionGeneration == expected) { "Session changed. Please log in again." }
            if (newSession) {
                sessionGeneration++
                privacy.bind(null)
            } else require(_session.value?.id == account.id)
            _session.value = account.toDomain()
        }
    }

    override suspend fun signup(
        name: String,
        identifier: String,
        password: CharArray,
        confirm: CharArray,
    ) = gate.withLock {
        val expected = generation()
        try {
            withContext(Dispatchers.IO) {
                val normalized = Validation.identifier(identifier)
                require(password.contentEquals(confirm)) { "Passwords do not match" }
                require(dao.account(normalized) == null) {
                    "That username/email is already used on this device"
                }
                val digest = hasher.create(password)
                val account =
                    AccountEntity(
                        UUID.randomUUID().toString(),
                        Validation.name(name),
                        normalized,
                        digest.salt,
                        digest.hash,
                        digest.iterations,
                    )
                dao.insertAccount(account)
                publish(expected, account, true)
            }
        } finally {
            password.fill('\u0000')
            confirm.fill('\u0000')
        }
    }

    override suspend fun login(identifier: String, password: CharArray) = gate.withLock {
        val expected = generation()
        try {
            withContext(Dispatchers.IO) {
                checkThrottle()
                val account = dao.account(Validation.identifier(identifier))
                val valid =
                    if (account != null)
                        hasher.verify(password, account.salt, account.hash, account.iterations)
                    else {
                        hasher.derive(password, ByteArray(32), 600000)
                        false
                    }
                if (!valid) {
                    failed()
                    error("Incorrect username/email or password")
                }
                failures = 0
                retryAfter = null
                publish(expected, requireNotNull(account), true)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    override fun requireAccount(): LocalAccount = _session.value ?: error("Please log in again")

    override suspend fun verify(password: CharArray): Boolean = gate.withLock {
        val expected = generation()
        val accountId = requireAccount().id
        try {
            withContext(Dispatchers.IO) {
                checkThrottle()
                checkSession(expected, accountId)
                val account = dao.accountById(accountId) ?: error("Account no longer exists")
                hasher.verify(password, account.salt, account.hash, account.iterations).also {
                    checkSession(expected, accountId)
                    if (it) {
                        failures = 0
                        retryAfter = null
                    } else failed()
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun changePassword(old: CharArray, next: CharArray, confirm: CharArray) {
        val expected = generation()
        val accountId = requireAccount().id
        try {
            require(verify(old)) { "Incorrect current password" }
            require(next.contentEquals(confirm)) { "Passwords do not match" }
            withContext(Dispatchers.IO) {
                checkSession(expected, accountId)
                val account = dao.accountById(accountId) ?: error("Account no longer exists")
                val digest = hasher.create(next)
                val updated =
                    account.copy(
                        salt = digest.salt,
                        hash = digest.hash,
                        iterations = digest.iterations,
                        biometricCipher = null,
                        biometricIv = null,
                    )
                checkSession(expected, accountId)
                currentCoroutineContext().ensureActive()
                dao.updateAccount(updated)
                publish(expected, updated, false)
            }
        } finally {
            old.fill('\u0000')
            next.fill('\u0000')
            confirm.fill('\u0000')
        }
    }

    override suspend fun biometricAccount(identifier: String) =
        withContext(Dispatchers.IO) { dao.account(Validation.identifier(identifier))?.toDomain() }

    override suspend fun enableBiometric(accountId: String, ciphertext: ByteArray, iv: ByteArray) {
        val expected = generation()
        withContext(Dispatchers.IO) {
            val account = dao.accountById(requireAccount().id) ?: error("Account no longer exists")
            require(account.id == accountId)
            val updated = account.copy(biometricCipher = ciphertext, biometricIv = iv)
            checkSession(expected, accountId)
            dao.updateAccount(updated)
            publish(expected, updated, false)
        }
    }

    override suspend fun disableBiometric() {
        val expected = generation()
        val accountId = requireAccount().id
        withContext(Dispatchers.IO) {
            val updated =
                (dao.accountById(requireAccount().id) ?: error("Account no longer exists")).copy(
                    biometricCipher = null,
                    biometricIv = null,
                )
            checkSession(expected, accountId)
            dao.updateAccount(updated)
            publish(expected, updated, false)
        }
    }

    override suspend fun biometricLogin(account: LocalAccount, decrypted: ByteArray) =
        gate.withLock {
            val expected = generation()
            try {
                require(
                    java.security.MessageDigest.isEqual(
                        decrypted,
                        account.id.toByteArray(Charsets.UTF_8),
                    )
                ) {
                    "Biometric unlock failed. Use your password."
                }
                withContext(Dispatchers.IO) {
                    val current = dao.accountById(account.id) ?: error("Account no longer exists")
                    require(
                        current.biometricCipher?.contentEquals(
                            account.biometricCipher ?: byteArrayOf()
                        ) == true
                    ) {
                        "Biometric setup changed. Use your password."
                    }
                    publish(expected, current, true)
                }
            } finally {
                decrypted.fill(0)
            }
        }

    override fun logout() {
        synchronized(sessionLock) {
            sessionGeneration++
            privacy.bind(null)
            _session.value = null
        }
    }

    private fun checkThrottle() {
        require(retryAfter?.let { System.nanoTime() - it >= 0 } ?: true) {
            "Too many attempts. Wait 30 seconds and try again."
        }
    }

    private fun failed() {
        failures++
        if (failures >= 5) retryAfter = System.nanoTime() + 30_000_000_000L
    }
}
