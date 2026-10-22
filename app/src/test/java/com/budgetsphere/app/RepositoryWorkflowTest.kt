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
        settings=Settings(context);finance=FinanceRepository(db,accounts,settings,PrivacyManager(),ReceiptPaths(context),db.dao());receipts=ReceiptStore(context,finance,ReceiptPaths(context));portability=Portability(context,finance,accounts,receipts,settings)
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
        try{finance.saveTransaction(Scope(account,friend.id),db.dao().transaction(account,personal.id,id)?.toDomain(),"Bad edit","1","EXPENSE",category.id,LocalDate.now(),"","","","");fail("Cross-profile mutation accepted")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}
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
        val orphan=receipts.file(scope,"${newId()}.jpg").apply {writeText("interrupted import");setLastModified(System.currentTimeMillis()-172800000L)}
        file.setLastModified(System.currentTimeMillis()-172800000L)
        receipts.collectOrphans(scope);assertFalse(orphan.exists());assertTrue(file.exists())
        finance.deleteTransaction(scope,tx);assertFalse(file.exists());assertFalse(thumb.exists());assertTrue(retained.exists())
        finance.deleteProfile(friend.id);assertFalse(retained.exists())
    }
    @Test fun receiptImportsNormalizeCameraOrientation()=runBlocking {
        signup();val account=accounts.requireAccount().id;val p=profile("Camera");val scope=Scope(account,p.id)
        val category=db.dao().categoryList(account,p.id).first {it.kind=="EXPENSE"}
        val tx=finance.saveTransaction(scope,null,"Camera receipt","1","EXPENSE",category.id,LocalDate.now(),"","","","")
        val image=File(context.cacheDir,"rotated.jpg").apply {writeBytes(java.util.Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAAAAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAIABADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwAooopn5wf/2Q=="))}
        receipts.import(scope,tx,Uri.fromFile(image))
        val saved=db.dao().attachmentList(account,p.id).single()
        val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeFile(receipts.file(scope,saved.filename).path,bounds)
        assertEquals(8,bounds.outWidth);assertEquals(16,bounds.outHeight)
    }
    @Test fun backupRoundTripPreservesRelationshipsAndRejectsTamperingWithoutOverwrites()=runBlocking {
        signup();val account=accounts.requireAccount().id;val p=profile("Personal");val scope=Scope(account,p.id);finance.select(p.id)
        val category=db.dao().categoryList(account,p.id).first {it.kind=="EXPENSE"}
        val transactionId=finance.saveTransaction(scope,null,"Lunch","123.45","EXPENSE",category.id,LocalDate.now(),"description","notes","Cash","food")
        val receiptImage=File(context.cacheDir,"receipt.png").apply {writeBytes(java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFElEQVR4nGMU2NnIgA0wYRUdtBIABeIBWu28MT4AAAAASUVORK5CYII="))}
        receipts.import(scope,transactionId,Uri.fromFile(receiptImage))
        finance.saveBudget(scope,null,category.id,"2026-10","1000")
        val file=File(context.cacheDir,"test-backup.bsp")
        portability.backup(account,Uri.fromFile(file),"a separate backup secret".toCharArray())
        val encrypted=file.readBytes();assertFalse(String(encrypted).contains("Lunch"))
        portability.restore(account,Uri.fromFile(file),"a separate backup secret".toCharArray())
        val profiles=db.dao().profileList(account);assertEquals(2,profiles.size)
        val restored=profiles.single {it.id!=p.id};val tx=db.dao().transactionList(account,restored.id).single();val budget=db.dao().budgetList(account,restored.id).single()
        assertEquals(12345L,tx.amount);assertEquals("notes",tx.notes);assertEquals(tx.categoryId,budget.categoryId)
        assertEquals(1,db.dao().transactionList(account,p.id).size)
        val restoredAttachment=db.dao().attachmentList(account,restored.id).single()
        assertEquals(tx.id,restoredAttachment.transactionId)
        assertTrue(receipts.file(Scope(account,restored.id),restoredAttachment.filename).isFile)
        val rawBackup=BackupCipher().decrypt(encrypted,"a separate backup secret".toCharArray())
        listOf("amount","preferences").forEach {invalidField->
            val malformed=org.json.JSONObject(String(rawBackup,Charsets.UTF_8))
            if(invalidField=="amount")malformed.getJSONArray("profiles").getJSONObject(0).getJSONArray("transactions").getJSONObject(0).put("amount",1.5)
            else malformed.getJSONObject("preferences").put("autoHide","false")
            file.writeBytes(BackupCipher().encrypt(malformed.toString().toByteArray(),"a separate backup secret".toCharArray()))
            try{portability.restore(account,Uri.fromFile(file),"a separate backup secret".toCharArray());fail("Malformed backup accepted")}catch(_:IllegalArgumentException){}
            assertEquals(2,db.dao().profileList(account).size)
        }
        rawBackup.fill(0)
        file.writeBytes(encrypted.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
        try{portability.restore(account,Uri.fromFile(file),"a separate backup secret".toCharArray());fail("Tampered backup accepted")}catch(_:IllegalStateException){}
        assertEquals(2,db.dao().profileList(account).size)
        val json=File(context.cacheDir,"export.json");portability.export(scope,Uri.fromFile(json),false)
        assertTrue(json.readText().contains("Lunch"));assertFalse(json.readText().contains("restored"))
    }
}
