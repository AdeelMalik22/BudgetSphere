package com.budgetsphere.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.budgetsphere.app.core.*
import com.budgetsphere.app.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

@Singleton class Portability @Inject constructor(@param:ApplicationContext private val context: Context,private val finance: FinanceRepository,private val accounts: AccountRepository,private val receipts: ReceiptStore,private val settings: Settings) {
    private fun array(values: List<JSONObject>)=JSONArray().apply {values.forEach {put(it)}}
    private fun objectOf(vararg pairs: Pair<String,Any?>)=JSONObject().apply {pairs.forEach {(k,v)->put(k,v?:JSONObject.NULL)}}
    private suspend fun profileJson(scope: Scope,includeReceipts: Boolean): JSONObject {
        val p=finance.validate(scope)
        val categories=finance.dao.categoryList(scope.account,scope.profile)
        val transactions=finance.dao.transactionList(scope.account,scope.profile)
        val budgets=finance.dao.budgetList(scope.account,scope.profile)
        val attachments=if(includeReceipts)finance.dao.attachmentList(scope.account,scope.profile) else emptyList()
        return objectOf("id" to p.id,"name" to p.name,"description" to p.description,"currency" to p.currency,"color" to p.color,"icon" to p.icon,
            "categories" to array(categories.map {objectOf("id" to it.id,"name" to it.name,"kind" to it.kind,"color" to it.color,"icon" to it.icon,"custom" to it.custom)}),
            "transactions" to array(transactions.map {objectOf("id" to it.id,"title" to it.title,"amount" to it.amount,"currency" to it.currency,"kind" to it.kind,"category" to it.categoryId,"date" to it.date,"description" to it.description,"notes" to it.notes,"payment" to it.payment,"tags" to it.tags,"created" to it.createdAt,"updated" to it.updatedAt)}),
            "budgets" to array(budgets.map {objectOf("id" to it.id,"category" to it.categoryId,"month" to it.month,"amount" to it.amount)}),
            "attachments" to array(attachments.map {a->val file=receipts.file(scope,a.filename);require(file.isFile && file.length()<=10*1024*1024){"A receipt is missing or too large. Remove or replace it before backing up."};objectOf("id" to a.id,"transaction" to a.transactionId,"image" to Base64.encodeToString(file.readBytes(),Base64.NO_WRAP),"thumbnail" to Base64.encodeToString(receipts.file(scope,a.thumbnail).readBytes(),Base64.NO_WRAP))}))
    }
    suspend fun export(scope: Scope,uri: Uri,csv: Boolean)=withContext(Dispatchers.IO) {
        finance.validate(scope)
        require(finance.workspace.value?.scope==scope){"Workspace changed. Start export again."}
        val content=if(!csv) objectOf("format" to "BudgetSphere profile export","version" to 1,"profile" to profileJson(scope,false)).toString(2) else {
            val categories=finance.dao.categoryList(scope.account,scope.profile).associateBy {it.id}
            fun quote(value: String): String {val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$value" else value;return "\"${safe.replace("\"","\"\"")}\""}
            buildString {append("date,type,title,amount,currency,category,description,notes,payment,tags\r\n");finance.dao.transactionList(scope.account,scope.profile).forEach {tx->append(listOf(LocalDate.ofEpochDay(tx.date).toString(),tx.kind,tx.title,Money.decimal(tx.amount,tx.currency),tx.currency,categories[tx.categoryId]?.name?:"",tx.description,tx.notes,tx.payment,tx.tags).joinToString(",",transform=::quote));append("\r\n")}}
        }
        context.contentResolver.openOutputStream(uri,"wt").use {requireNotNull(it){"Cannot open export destination"};it.write(content.toByteArray(Charsets.UTF_8))}
    }
    suspend fun backup(account: String,uri: Uri,password: CharArray)=withContext(Dispatchers.IO) {
        try {
            require(accounts.requireAccount().id==account)
            val profiles=finance.dao.profileList(account)
            val prefs=settings.preferences.first()
            val json=finance.db.withTransaction { objectOf("format" to "BudgetSphere account backup","version" to 1,"profiles" to array(profiles.map {profileJson(Scope(account,it.id),true)}),"preferences" to objectOf("theme" to prefs.theme,"autoHide" to prefs.autoHide,"screenshots" to prefs.screenshotProtection,"monday" to prefs.mondayFirst,"dateFormat" to prefs.dateFormat)) }
            require(accounts.requireAccount().id==account)
            val plain=json.toString().toByteArray(Charsets.UTF_8)
            val encrypted=try{BackupCipher().encrypt(plain,password)}finally{plain.fill(0)}
            context.contentResolver.openOutputStream(uri,"wt").use {requireNotNull(it){"Cannot open backup destination"};it.write(encrypted)}
        }finally{password.fill('\u0000')}
    }
    private fun objects(json: JSONObject,key: String,max: Int): List<JSONObject> {val a=json.getJSONArray(key);require(a.length()<=max){"Backup contains too many records"};return (0 until a.length()).map {a.getJSONObject(it)}}
    private fun text(json: JSONObject,key: String,max: Int): String = json.getString(key).also {require(it.length<=max){"Invalid backup text"}}
    private fun unique(list: List<JSONObject>): Map<String,String> {val ids=list.map {text(it,"id",100)};require(ids.size==ids.toSet().size && ids.all {it.isNotBlank()}){"Duplicate or missing backup identifiers"};return ids.associateWith {newId()}}
    suspend fun restore(account: String,uri: Uri,password: CharArray)=withContext(Dispatchers.IO) {
        val staged=mutableListOf<File>()
        try {
            require(accounts.requireAccount().id==account)
            val encrypted=context.contentResolver.openInputStream(uri).use {input->requireNotNull(input);val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=BackupCipher.MAX_BYTES+72){"Backup is too large"};out.write(buffer,0,n)};out.toByteArray()}
            val plain=try {BackupCipher().decrypt(encrypted,password)}catch(_:javax.crypto.AEADBadTagException){error("Incorrect backup passphrase or damaged backup")}
            val root=try{JSONObject(String(plain,Charsets.UTF_8))}finally{plain.fill(0)}
            require(root.getString("format")=="BudgetSphere account backup" && root.getInt("version")==1){"Unsupported backup version"}
            val profiles=objects(root,"profiles",100);unique(profiles)
            val newProfiles=mutableListOf<ProfileEntity>();val newCategories=mutableListOf<CategoryEntity>();val newTransactions=mutableListOf<TransactionEntity>();val newBudgets=mutableListOf<BudgetEntity>();val newAttachments=mutableListOf<AttachmentEntity>()
            profiles.forEach {p->
                val id=newId();val scope=Scope(account,id);val currency=text(p,"currency",3);require(Currency.getInstance(currency).defaultFractionDigits>=0)
                newProfiles+=ProfileEntity(account,id,Validation.name(text(p,"name",100)).take(88)+" (restored)",text(p,"description",1000),currency,p.getLong("color"),text(p,"icon",40))
                val cats=objects(p,"categories",1000);val categoryMap=unique(cats);val catKinds=cats.associate {it.getString("id") to it.getString("kind")}
                val names=hashSetOf<String>();cats.forEach {c->val kind=text(c,"kind",10);require(kind in listOf("EXPENSE","INCOME"));val name=Validation.name(text(c,"name",100));require(names.add("$kind:$name"));newCategories+=CategoryEntity(account,id,categoryMap.getValue(c.getString("id")),name,kind,c.getLong("color"),text(c,"icon",40),c.getBoolean("custom"))}
                val txs=objects(p,"transactions",100000);val txMap=unique(txs)
                txs.forEach {tx->val amount=tx.getLong("amount");require(amount in 1..100_000_000_000_000L);val category=tx.getString("category");val kind=text(tx,"kind",10);require(catKinds[category]==kind);require(tx.getString("currency")==currency);val date=tx.getLong("date");require(LocalDate.ofEpochDay(date).year in 1900..2200);newTransactions+=TransactionEntity(account,id,txMap.getValue(tx.getString("id")),Validation.name(text(tx,"title",100)),amount,currency,kind,categoryMap.getValue(category),date,text(tx,"description",4000),text(tx,"notes",4000),text(tx,"payment",100),text(tx,"tags",500),tx.getLong("created"),tx.getLong("updated"))}
                require(Money.sum(newTransactions.filter {it.profileId==id}.map {it.amount})<=1_000_000_000_000_000_000L)
                val budgets=objects(p,"budgets",10000);unique(budgets);val budgetKeys=hashSetOf<String>()
                budgets.forEach {b->val sourceCategory=if(b.isNull("category"))null else b.getString("category");if(sourceCategory!=null)require(catKinds[sourceCategory]=="EXPENSE");val category=sourceCategory?.let {categoryMap.getValue(it)};val month=text(b,"month",7);require(YearMonth.parse(month).year in 1900..2200);require(budgetKeys.add("$month:$category"));val amount=b.getLong("amount");require(amount in 1..100_000_000_000_000L);newBudgets+=BudgetEntity(account,id,newId(),category,category?:"overall",month,amount)}
                val attachments=objects(p,"attachments",10000);unique(attachments)
                if(attachments.isNotEmpty()){val dir=receipts.directory(scope);check(dir.mkdirs());staged+=dir}
                attachments.forEach {a->val aid=newId();val tx=txMap[a.getString("transaction")]?:error("Invalid receipt transaction");val image=Base64.decode(text(a,"image",15*1024*1024),Base64.NO_WRAP);val thumb=Base64.decode(text(a,"thumbnail",2*1024*1024),Base64.NO_WRAP);require(image.size<=10*1024*1024 && thumb.size<=1024*1024);validateImage(image);validateImage(thumb);receipts.file(scope,"$aid.jpg").writeBytes(image);receipts.file(scope,"$aid-thumb.jpg").writeBytes(thumb);newAttachments+=AttachmentEntity(account,id,aid,tx,"$aid.jpg","$aid-thumb.jpg")}
            }
            val prefs=root.getJSONObject("preferences");val theme=text(prefs,"theme",10);require(theme in listOf("System","Light","Dark"));val format=text(prefs,"dateFormat",30);require(format in listOf("dd MMM yyyy","yyyy-MM-dd","MM/dd/yyyy"))
            finance.db.withTransaction {
                require(accounts.requireAccount().id==account)
                newProfiles.forEach {finance.dao.putProfile(it)};newCategories.forEach {finance.dao.putCategory(it)};newTransactions.forEach {finance.dao.putTransaction(it)};newBudgets.forEach {finance.dao.putBudget(it)};newAttachments.forEach {finance.dao.insertAttachment(it)}
            }
            staged.clear() // Database now owns these files. Existing profiles were never replaced.
            settings.set("theme",theme);settings.set("autoHide",prefs.getBoolean("autoHide"));settings.set("screenshots",prefs.getBoolean("screenshots"));settings.set("monday",prefs.getBoolean("monday"));settings.set("dateFormat",format)
        } finally {password.fill('\u0000');staged.forEach {it.deleteRecursively()}}
    }
    private fun validateImage(bytes: ByteArray) {val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds);require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong()*bounds.outHeight<=16_777_216){"Invalid receipt image in backup"}}
}
