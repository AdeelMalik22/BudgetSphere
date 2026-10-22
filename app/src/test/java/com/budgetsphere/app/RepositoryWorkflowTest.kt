package com.budgetsphere.app

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class RepositoryWorkflowTest {
    private lateinit var db: BudgetDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var finance: FinanceRepository
    private lateinit var portability: Portability
    private lateinit var settings: Settings
    private lateinit var receipts: ReceiptStore
    private lateinit var context: Application
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        db=Room.inMemoryDatabaseBuilder(context,BudgetDatabase::class.java).build()
        accounts=AccountRepository(db.dao(),PasswordHasher(),PrivacyManager())
        settings=Settings(context);finance=FinanceRepository(db,accounts,settings,PrivacyManager(),ReceiptPaths(context));receipts=ReceiptStore(context,finance,ReceiptPaths(context));portability=Portability(context,finance,accounts,receipts,settings)
    }
    @After fun close(){db.close()}
    private suspend fun signup(name: String="owner") {accounts.signup(name,name,"a secure local password".toCharArray(),"a secure local password".toCharArray())}
    private suspend fun profile(name:String)=finance.saveProfile(null,name,"","PKR",0xFF047857,"Person")
    @Test fun accountsProfilesAndMutationsEnforceOwnership()=runBlocking {
        signup();val account=accounts.requireAccount().id;val personal=profile("Personal");val friend=profile("Friend");val scope=Scope(account,personal.id)
        val category=db.dao().categoryList(account,personal.id).first {it.kind=="EXPENSE"}
        val id=finance.saveTransaction(scope,null,"Groceries","500.25","EXPENSE",category.id,LocalDate.now(),"","","Cash","")
        assertEquals(50025L,db.dao().transaction(account,personal.id,id)?.amount)
        assertTrue(db.dao().transactionList(account,friend.id).isEmpty())
        try{finance.saveTransaction(Scope(account,friend.id),db.dao().transaction(account,personal.id,id),"Bad edit","1","EXPENSE",category.id,LocalDate.now(),"","","","");fail("Cross-profile mutation accepted")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}
        accounts.logout();signup("other")
        try{finance.deleteTransaction(scope,id);fail("Cross-account mutation accepted")}catch(_:IllegalArgumentException){}
        assertNotNull(db.dao().transaction(account,personal.id,id))
        accounts.logout();accounts.login("owner","a secure local password".toCharArray());assertEquals(account,accounts.requireAccount().id)
        assertFalse(accounts.verify("wrong password".toCharArray()))
        accounts.changePassword("a secure local password".toCharArray(),"my changed secure password".toCharArray(),"my changed secure password".toCharArray());accounts.logout()
        try{accounts.login("owner","a secure local password".toCharArray());fail("Old password accepted")}catch(_:IllegalStateException){}
        accounts.login("owner","my changed secure password".toCharArray());assertEquals(account,accounts.requireAccount().id)
    }
    @Test fun deletionsRemoveOwnedReceiptFilesWithoutTouchingOtherProfiles()=runBlocking {
        signup();val account=accounts.requireAccount().id;val personal=profile("Personal");val friend=profile("Friend")
        val scope=Scope(account,personal.id);val other=Scope(account,friend.id)
        val category=db.dao().categoryList(account,personal.id).first {it.kind=="EXPENSE"}
        val tx=finance.saveTransaction(scope,null,"Receipt test","1","EXPENSE",category.id,LocalDate.now(),"","","","")
        val receipt=newId();val file=receipts.file(scope,"$receipt.jpg");file.parentFile!!.mkdirs();file.writeText("private receipt")
        val thumb=receipts.file(scope,"$receipt-thumb.jpg");thumb.writeText("private thumbnail")
        db.dao().insertAttachment(AttachmentEntity(account,personal.id,receipt,tx,file.name,thumb.name))
        val retained=receipts.file(other,"${newId()}.jpg");retained.parentFile!!.mkdirs();retained.writeText("other profile")
        finance.deleteTransaction(scope,tx);assertFalse(file.exists());assertFalse(thumb.exists());assertTrue(retained.exists())
        finance.deleteProfile(friend.id);assertFalse(retained.exists())
    }
    @Test fun backupRoundTripPreservesRelationshipsAndRejectsTamperingWithoutOverwrites()=runBlocking {
        signup();val account=accounts.requireAccount().id;val p=profile("Personal");val scope=Scope(account,p.id);finance.select(p.id)
        val category=db.dao().categoryList(account,p.id).first {it.kind=="EXPENSE"}
        finance.saveTransaction(scope,null,"Lunch","123.45","EXPENSE",category.id,LocalDate.now(),"description","notes","Cash","food")
        finance.saveBudget(scope,null,category.id,"2026-10","1000")
        val file=File(context.cacheDir,"test-backup.bsp")
        portability.backup(account,Uri.fromFile(file),"a separate backup secret".toCharArray())
        val encrypted=file.readBytes();assertFalse(String(encrypted).contains("Lunch"))
        portability.restore(account,Uri.fromFile(file),"a separate backup secret".toCharArray())
        val profiles=db.dao().profileList(account);assertEquals(2,profiles.size)
        val restored=profiles.single {it.id!=p.id};val tx=db.dao().transactionList(account,restored.id).single();val budget=db.dao().budgetList(account,restored.id).single()
        assertEquals(12345L,tx.amount);assertEquals("notes",tx.notes);assertEquals(tx.categoryId,budget.categoryId)
        assertEquals(1,db.dao().transactionList(account,p.id).size)
        file.writeBytes(encrypted.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
        try{portability.restore(account,Uri.fromFile(file),"a separate backup secret".toCharArray());fail("Tampered backup accepted")}catch(_:IllegalStateException){}
        assertEquals(2,db.dao().profileList(account).size)
        val json=File(context.cacheDir,"export.json");portability.export(scope,Uri.fromFile(json),false)
        assertTrue(json.readText().contains("Lunch"));assertFalse(json.readText().contains("restored"))
    }
}
