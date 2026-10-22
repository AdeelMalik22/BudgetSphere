package com.budgetsphere.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts", indices = [Index(value = ["identifier"], unique = true)])
data class AccountEntity(@PrimaryKey val id: String, val name: String, val identifier: String, val salt: ByteArray, val hash: ByteArray, val iterations: Int = 600000, val biometricCipher: ByteArray? = null, val biometricIv: ByteArray? = null)
@Entity(tableName = "profiles", primaryKeys = ["accountId", "id"], foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)])
data class ProfileEntity(val accountId: String, val id: String, val name: String, val description: String = "", val currency: String = "PKR", val color: Long = 0xFF10B981, val icon: String = "Person")
@Entity(tableName = "categories", primaryKeys = ["accountId", "profileId", "id"], foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["accountId", "id"], childColumns = ["accountId", "profileId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value=["accountId", "profileId", "name", "kind"], unique=true)])
data class CategoryEntity(val accountId: String, val profileId: String, val id: String, val name: String, val kind: String = "EXPENSE", val color: Long = 0xFF10B981, val icon: String = "Category", val custom: Boolean = false)
@Entity(tableName = "transactions", primaryKeys = ["accountId", "profileId", "id"], foreignKeys = [
    ForeignKey(entity = ProfileEntity::class, parentColumns=["accountId","id"], childColumns=["accountId","profileId"], onDelete=ForeignKey.CASCADE),
    ForeignKey(entity = CategoryEntity::class, parentColumns=["accountId","profileId","id"], childColumns=["accountId","profileId","categoryId"], onDelete=ForeignKey.RESTRICT)
], indices = [Index(value=["accountId","profileId","date"]), Index(value=["accountId","profileId","categoryId"])])
data class TransactionEntity(val accountId: String, val profileId: String, val id: String, val title: String, val amount: Long, val currency: String, val kind: String, val categoryId: String, val date: Long, val description: String = "", val notes: String = "", val payment: String = "", val tags: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
@Entity(tableName = "budgets", primaryKeys=["accountId","profileId","id"], foreignKeys=[ForeignKey(entity=ProfileEntity::class,parentColumns=["accountId","id"],childColumns=["accountId","profileId"],onDelete=ForeignKey.CASCADE), ForeignKey(entity=CategoryEntity::class,parentColumns=["accountId","profileId","id"],childColumns=["accountId","profileId","categoryId"],onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["accountId","profileId","categoryId"]), Index(value=["accountId","profileId","month","categoryKey"],unique=true)])
data class BudgetEntity(val accountId: String, val profileId: String, val id: String, val categoryId: String?, val categoryKey: String, val month: String, val amount: Long)
@Entity(tableName="attachments", primaryKeys=["accountId","profileId","id"],foreignKeys=[ForeignKey(entity=TransactionEntity::class,parentColumns=["accountId","profileId","id"],childColumns=["accountId","profileId","transactionId"],onDelete=ForeignKey.CASCADE)],indices=[Index(value=["accountId","profileId","transactionId"])])
data class AttachmentEntity(val accountId: String, val profileId: String, val id: String, val transactionId: String, val filename: String, val thumbnail: String)
data class Summary(val income: Long, val expense: Long)

@Dao interface FinanceDao {
    @Query("SELECT * FROM accounts WHERE identifier=:identifier LIMIT 1") suspend fun account(identifier: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun accountById(id: String): AccountEntity?
    @Insert suspend fun insertAccount(account: AccountEntity)
    @Update suspend fun updateAccount(account: AccountEntity)
    @Query("DELETE FROM accounts WHERE id=:account") suspend fun deleteAccount(account: String)
    @Query("SELECT * FROM profiles WHERE accountId=:account ORDER BY name") fun profiles(account: String): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE accountId=:account") suspend fun profileList(account: String): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE accountId=:account AND id=:profile") suspend fun profile(account: String, profile: String): ProfileEntity?
    @Upsert suspend fun putProfile(profile: ProfileEntity)
    @Query("DELETE FROM profiles WHERE accountId=:account AND id=:profile") suspend fun deleteProfile(account: String, profile: String)
    @Query("SELECT * FROM categories WHERE accountId=:account AND profileId=:profile ORDER BY kind,name") fun categories(account: String, profile: String): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE accountId=:account AND profileId=:profile") suspend fun categoryList(account: String, profile: String): List<CategoryEntity>
    @Upsert suspend fun putCategory(category: CategoryEntity)
    @Query("DELETE FROM categories WHERE accountId=:account AND profileId=:profile AND id=:id") suspend fun deleteCategory(account: String, profile: String, id: String)
    @Query("UPDATE transactions SET categoryId=:target WHERE accountId=:account AND profileId=:profile AND categoryId=:source") suspend fun reassign(account: String, profile: String, source: String, target: String)
    @Query("UPDATE budgets SET categoryId=:target,categoryKey=:target WHERE accountId=:account AND profileId=:profile AND categoryId=:source") suspend fun reassignBudgets(account: String, profile: String, source: String, target: String)
    @Query("SELECT * FROM transactions WHERE accountId=:account AND profileId=:profile AND date BETWEEN :start AND :end AND (:kind='' OR kind=:kind) AND (:category='' OR categoryId=:category) AND (instr(lower(title),lower(:search))>0 OR instr(lower(tags),lower(:search))>0) AND amount BETWEEN :minimum AND :maximum ORDER BY CASE WHEN :sort='oldest' THEN date END ASC, CASE WHEN :sort='amount' THEN amount END DESC, date DESC, createdAt DESC LIMIT :limit")
    fun transactions(account: String, profile: String, start: Long, end: Long, kind: String, category: String, search: String, minimum: Long, maximum: Long, sort: String, limit: Int): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE accountId=:account AND profileId=:profile ORDER BY date DESC, createdAt DESC") fun observeTransactions(account: String, profile: String): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE accountId=:account AND profileId=:profile ORDER BY date DESC") suspend fun transactionList(account: String, profile: String): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE accountId=:account AND profileId=:profile AND id=:id") suspend fun transaction(account: String, profile: String, id: String): TransactionEntity?
    @Query("SELECT COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0) AS income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount ELSE 0 END),0) AS expense FROM transactions WHERE accountId=:account AND profileId=:profile AND date BETWEEN :start AND :end") fun summary(account: String, profile: String, start: Long, end: Long): Flow<Summary>
    @Upsert suspend fun putTransaction(transaction: TransactionEntity)
    @Query("DELETE FROM transactions WHERE accountId=:account AND profileId=:profile AND id=:id") suspend fun deleteTransaction(account: String, profile: String, id: String)
    @Query("SELECT * FROM budgets WHERE accountId=:account AND profileId=:profile ORDER BY month DESC") fun budgets(account: String, profile: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE accountId=:account AND profileId=:profile") suspend fun budgetList(account: String, profile: String): List<BudgetEntity>
    @Upsert suspend fun putBudget(budget: BudgetEntity)
    @Query("DELETE FROM budgets WHERE accountId=:account AND profileId=:profile AND id=:id") suspend fun deleteBudget(account: String, profile: String, id: String)
    @Query("SELECT * FROM attachments WHERE accountId=:account AND profileId=:profile") fun attachments(account: String, profile: String): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments WHERE accountId=:account AND profileId=:profile") suspend fun attachmentList(account: String, profile: String): List<AttachmentEntity>
    @Insert suspend fun insertAttachment(attachment: AttachmentEntity)
    @Query("DELETE FROM attachments WHERE accountId=:account AND profileId=:profile AND id=:id") suspend fun deleteAttachment(account: String, profile: String, id: String)
}
@Database(entities=[AccountEntity::class, ProfileEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class, AttachmentEntity::class],version=1,exportSchema=true)
abstract class BudgetDatabase: RoomDatabase() { abstract fun dao(): FinanceDao }
