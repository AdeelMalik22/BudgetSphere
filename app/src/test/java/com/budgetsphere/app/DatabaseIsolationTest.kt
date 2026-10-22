package com.budgetsphere.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.budgetsphere.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DatabaseIsolationTest {
    private lateinit var db: BudgetDatabase
    private lateinit var dao: FinanceDao
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),BudgetDatabase::class.java).allowMainThreadQueries().build();dao=db.dao()
        listOf("a","b").forEach {account->dao.insertAccount(AccountEntity(account,account,account,ByteArray(32),ByteArray(32)));listOf("personal","friend").forEach {profile->dao.putProfile(ProfileEntity(account,profile,profile));dao.putCategory(CategoryEntity(account,profile,"food","Food"))}}
    }
    @After fun close(){db.close()}
    private fun tx(account:String,profile:String,id:String="tx")=TransactionEntity(account,profile,id,"Lunch",500,"PKR","EXPENSE","food",1)
    @Test fun readsAggregatesAndMutationsAreScoped()=runBlocking {
        dao.putTransaction(tx("a","personal"));dao.putTransaction(tx("a","friend").copy(amount=900));dao.putTransaction(tx("b","personal").copy(amount=1200))
        assertEquals(500,dao.summary("a","personal",0,2).first().expense)
        assertEquals(900,dao.summary("a","friend",0,2).first().expense)
        dao.deleteTransaction("a","personal","tx")
        assertTrue(dao.transactionList("a","personal").isEmpty())
        assertEquals(1,dao.transactionList("a","friend").size)
        assertEquals(1,dao.transactionList("b","personal").size)
    }
    @Test fun foreignKeysRejectCrossProfileCategoriesAndCascadeOnlyOwnedData()=runBlocking {
        dao.putCategory(CategoryEntity("a","personal","private","Private"))
        try {dao.putTransaction(tx("a","friend").copy(categoryId="private"));fail("Expected foreign-key rejection")}catch(_:android.database.sqlite.SQLiteConstraintException){}
        dao.putTransaction(tx("a","personal"));dao.putTransaction(tx("b","personal"))
        try {dao.deleteCategory("a","personal","food");fail("Expected restrict behavior")}catch(_:android.database.sqlite.SQLiteConstraintException){}
        dao.insertAttachment(AttachmentEntity("a","personal","receipt","tx","receipt.jpg","thumb.jpg"))
        dao.deleteProfile("a","personal")
        assertTrue(dao.attachmentList("a","personal").isEmpty());assertTrue(dao.transactionList("a","personal").isEmpty());assertEquals(1,dao.transactionList("b","personal").size)
    }
    @Test fun filtersDatesAndUpdatesRemainScoped()=runBlocking {
        dao.putTransaction(tx("a","personal","old").copy(date=1));dao.putTransaction(tx("a","personal","new").copy(date=5,title="Bus",amount=700))
        val result=dao.transactions("a","personal",2,6,"EXPENSE","food","bus",600,800,"newest",100).first()
        assertEquals(listOf("new"),result.map {it.id})
        dao.putTransaction(result.single().copy(amount=750));assertEquals(750L,dao.transaction("a","personal","new")?.amount)
    }
}
