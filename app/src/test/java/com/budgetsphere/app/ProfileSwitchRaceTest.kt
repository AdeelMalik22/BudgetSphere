package com.budgetsphere.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileSwitchRaceTest {
    @Test
    fun lateProfileLoadsCannotReplaceNewerWorkspaceOrSavedSelection() = runBlocking {
        verifyLatestWins(false)
    }

    @Test
    fun lateStartupRestorationCannotReplaceManualSelection() = runBlocking {
        verifyLatestWins(true)
    }

    private suspend fun verifyLatestWins(restoring: Boolean) = coroutineScope {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).build()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observed = java.util.concurrent.ConcurrentLinkedQueue<String>()
        var collector: Job? = null
        try {
            val privacy = PrivacyManager()
            val accounts = AccountRepository(db.dao(), PasswordHasher(), privacy)
            val settings = Settings(context)
            accounts.signup(
                "Owner",
                "owner",
                "a secure local password".toCharArray(),
                "a secure local password".toCharArray(),
            )
            val account = accounts.requireAccount().id
            val first = newId()
            val second = newId()
            db.dao().putProfile(ProfileEntity(account, first, "Personal"))
            db.dao().putProfile(ProfileEntity(account, second, "Friend"))
            val delayed =
                object : FinanceDao by db.dao() {
                    override suspend fun profile(account: String, profile: String): ProfileEntity? {
                        if (profile == first) {
                            entered.complete(Unit)
                            release.await()
                        }
                        return db.dao().profile(account, profile)
                    }
                }
            val finance =
                FinanceRepository(db, accounts, settings, privacy, ReceiptPaths(context), delayed)
            collector =
                launch(Dispatchers.Unconfined) {
                    finance.workspace.filterNotNull().collect { observed += it.scope.profile }
                }
            settings.active(account, first)
            val old =
                async(Dispatchers.Default) {
                    runCatching {
                        if (restoring) finance.restoreLastProfile() else finance.select(first)
                    }
                }
            withTimeout(10000) { entered.await() }
            val current = async(start = CoroutineStart.UNDISPATCHED) { finance.select(second) }
            assertNull(finance.workspace.value)
            release.complete(Unit)
            assertTrue(old.await().isFailure)
            current.await()
            assertEquals(second, finance.workspace.value?.scope?.profile)
            assertEquals(second, settings.active(account).first())
            assertFalse(observed.contains(first))
            assertTrue(privacy.hidden.value)
        } finally {
            release.complete(Unit)
            collector?.cancelAndJoin()
            db.close()
        }
    }
}
