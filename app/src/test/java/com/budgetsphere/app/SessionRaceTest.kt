package com.budgetsphere.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SessionRaceTest {
    @Test fun logoutInvalidatesAnAlreadyRunningPasswordLogin()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),BudgetDatabase::class.java).build()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val real=PasswordHasher()
        val blocking=object: PasswordDerivation by real {
            override fun verify(password: CharArray,salt: ByteArray,expected: ByteArray,iterations: Int): Boolean {
                entered.countDown();check(release.await(10,TimeUnit.SECONDS));return real.verify(password,salt,expected,iterations)
            }
        }
        try {
            val accounts=AccountRepository(db.dao(),blocking,PrivacyManager())
            accounts.signup("Owner","owner","a secure local password".toCharArray(),"a secure local password".toCharArray())
            accounts.logout()
            val pending=async(Dispatchers.Default){runCatching {accounts.login("owner","a secure local password".toCharArray())}}
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            accounts.logout();release.countDown()
            assertTrue(pending.await().isFailure)
            assertNull(accounts.session.value)
        } finally {release.countDown();db.close()}
    }
}
