package com.budgetsphere.app.data

import androidx.room.withTransaction
import com.budgetsphere.app.core.Settings
import com.budgetsphere.app.core.PrivacyManager
import com.budgetsphere.app.domain.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Currency
import java.util.UUID
import java.time.LocalDate
import java.time.YearMonth

fun newId(): String = UUID.randomUUID().toString()
@Singleton class FinanceRepository @Inject constructor(val db: BudgetDatabase, private val accounts: AccountRepository, private val settings: Settings, private val privacy: PrivacyManager,private val receiptPaths: ReceiptPaths, val dao: FinanceDao): FinanceStore {
    private val selectionMutex=Mutex()
    private val selectionLock=Any()
    private var selectionGeneration=0L
    private val _workspace=MutableStateFlow<Workspace?>(null)
    override val workspace=_workspace.asStateFlow()
    private fun beginSelection(): Long=synchronized(selectionLock) {
        selectionGeneration++;_workspace.value=null;privacy.bind(null);selectionGeneration
    }
    override fun clearWorkspace() {beginSelection()}
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun profiles()=accounts.session.flatMapLatest { if(it==null) flowOf(emptyList()) else dao.profiles(it.id).map {list->list.map(ProfileEntity::toDomain)} }
    override suspend fun select(profileId: String) {loadSelection(profileId,beginSelection())}
    private suspend fun loadSelection(profileId: String,expected: Long) {
        selectionMutex.withLock {
            withContext(Dispatchers.IO) {
                val account=accounts.requireAccount().id
                val profile=dao.profile(account,profileId)?:error("Profile no longer exists")
                synchronized(selectionLock) {if(selectionGeneration!=expected || accounts.session.value?.id!=account)throw CancellationException("Workspace selection was replaced")}
                settings.active(account,profileId)
                currentCoroutineContext().ensureActive()
                synchronized(selectionLock) {
                    if(selectionGeneration!=expected || accounts.session.value?.id!=account)throw CancellationException("Workspace selection was replaced")
                    val scope=Scope(account,profileId);privacy.bind(scope);_workspace.value=Workspace(scope,profile.toDomain())
                }
            }
        }
    }
    suspend fun validate(scope: Scope): ProfileEntity {
        require(accounts.requireAccount().id==scope.account) { "Please log in again" }
        return dao.profile(scope.account,scope.profile)?:error("Profile no longer exists")
    }
    override suspend fun saveProfile(id: String?, name: String, description: String, currency: String, color: Long, icon: String) = withContext(Dispatchers.IO) {
        val account=accounts.requireAccount().id
        require(Currency.getInstance(currency).defaultFractionDigits>=0) {"Choose a supported monetary currency"}
        require(description.length<=1000)
        val existing=id?.let { dao.profile(account,it)?:error("Profile no longer exists") }
        if(existing!=null && existing.currency!=currency) require(dao.transactionList(account,existing.id).isEmpty() && dao.budgetList(account,existing.id).isEmpty()) { "Currency cannot change while transactions or budgets exist. Create a separate profile instead." }
        val profile=ProfileEntity(account,id?:newId(),Validation.name(name),description,currency,color,icon)
        db.withTransaction {
            dao.putProfile(profile)
            if(existing==null) defaults(profile).forEach { dao.putCategory(it) }
        }
        if(_workspace.value?.scope?.profile==profile.id) select(profile.id)
        profile.toDomain()
    }
    override suspend fun deleteProfile(id: String): Unit = withContext(Dispatchers.IO) {
        val account=accounts.requireAccount().id
        require(dao.profile(account,id)!=null)
        if(_workspace.value?.scope==Scope(account,id)) clearWorkspace()
        dao.deleteProfile(account,id)
        receiptPaths.directory(Scope(account,id)).deleteRecursively()
        Unit
    }
    override suspend fun saveTransaction(scope: Scope, original: FinancialTransaction?, title: String, amount: String, kind: String, category: String, date: LocalDate, description: String, notes: String, payment: String, tags: String): String = withContext(Dispatchers.IO) {
        val profile=validate(scope)
        require(kind in listOf("EXPENSE","INCOME"))
        require(description.length<=4000 && notes.length<=4000 && payment.length<=100 && tags.length<=500) { "Text is too long" }
        require(date.year in 1900..2200) { "Choose a date between 1900 and 2200" }
        val categoryEntity=dao.categoryList(scope.account,scope.profile).find { it.id==category && it.kind==kind }?:error("Choose a category")
        val previous=original?.let { require(it.accountId==scope.account && it.profileId==scope.profile); dao.transaction(scope.account,scope.profile,it.id)?:error("Transaction no longer exists") }
        val tx=TransactionEntity(scope.account,scope.profile,previous?.id?:newId(),Validation.name(title),Money.parse(amount,profile.currency),profile.currency,kind,categoryEntity.id,date.toEpochDay(),description,notes,payment,tags,previous?.createdAt?:System.currentTimeMillis())
        db.withTransaction {
            // Bound the total to preserve SQLite INTEGER SUM precision for the entire profile.
            val existing=dao.transactionList(scope.account,scope.profile).filter { it.id!=tx.id }
            require(Money.sum(existing.map { it.amount }+tx.amount)<=1_000_000_000_000_000_000L) { "Profile total is too large" }
            dao.putTransaction(tx)
        }
        tx.id
    }
    override suspend fun deleteTransaction(scope: Scope,id: String)=withContext(Dispatchers.IO) {
        validate(scope)
        val attachments=dao.attachmentList(scope.account,scope.profile).filter {it.transactionId==id}
        dao.deleteTransaction(scope.account,scope.profile,id)
        attachments.forEach {receiptPaths.file(scope,it.filename).delete();receiptPaths.file(scope,it.thumbnail).delete()}
    }
    override suspend fun deleteCurrentAccount(): Unit=withContext(Dispatchers.IO) {
        val account=accounts.requireAccount().id
        dao.deleteAccount(account)
        clearWorkspace();accounts.logout()
        receiptPaths.accountDirectory(account).deleteRecursively()
        Unit
    }
    override suspend fun saveCategory(scope: Scope,id: String?,name: String,kind: String,color: Long,icon: String)=withContext(Dispatchers.IO) {
        validate(scope);require(kind in listOf("EXPENSE","INCOME"))
        val previous=id?.let { dao.categoryList(scope.account,scope.profile).find { c->c.id==it }?:error("Category no longer exists") }
        require(previous==null || previous.custom) { "Default categories cannot be edited" }
        require(previous==null || previous.kind==kind) { "Category type cannot change" }
        dao.putCategory(CategoryEntity(scope.account,scope.profile,id?:newId(),Validation.name(name),kind,color,icon,true))
    }
    override suspend fun deleteCategory(scope: Scope,id: String,reassignTo: String)=withContext(Dispatchers.IO) {
        validate(scope)
        val categories=dao.categoryList(scope.account,scope.profile)
        val source=categories.find {it.id==id}?:error("Category no longer exists")
        require(source.custom) { "Default categories cannot be deleted" }
        val target=categories.find {it.id==reassignTo && it.id!=id && it.kind==source.kind}?:error("Choose a replacement category of the same type")
        db.withTransaction {
            val budgets=dao.budgetList(scope.account,scope.profile)
            require(budgets.none { b->b.categoryId==id && budgets.any { it.month==b.month && it.categoryId==target.id } }) { "Both categories have a budget for the same month. Remove one budget before reassignment." }
            dao.reassign(scope.account,scope.profile,id,target.id);dao.reassignBudgets(scope.account,scope.profile,id,target.id);dao.deleteCategory(scope.account,scope.profile,id)
        }
    }
    override suspend fun saveBudget(scope: Scope,id: String?,category: String?,month: String,amount: String)=withContext(Dispatchers.IO) {
        val profile=validate(scope);YearMonth.parse(month).also {require(it.year in 1900..2200)}
        if(category!=null) require(dao.categoryList(scope.account,scope.profile).any {it.id==category && it.kind=="EXPENSE"})
        if(id!=null) require(dao.budgetList(scope.account,scope.profile).any {it.id==id})
        dao.putBudget(BudgetEntity(scope.account,scope.profile,id?:newId(),category,category?:"overall",month,Money.parse(amount,profile.currency)))
    }
    override suspend fun deleteBudget(scope: Scope,id: String)=withContext(Dispatchers.IO) { validate(scope);dao.deleteBudget(scope.account,scope.profile,id) }
    override suspend fun restoreLastProfile() {
        val expected=beginSelection()
        withContext(Dispatchers.IO) {
            val account=accounts.requireAccount().id
            val saved=settings.active(account).first()
            val profiles=dao.profileList(account)
            (profiles.find {it.id==saved}?:profiles.firstOrNull())?.let {loadSelection(it.id,expected)}
        }
    }
    override fun observeWorkspace(workspace: Workspace): Flow<WorkspaceSnapshot> = flow {
        val scope=workspace.scope
        validate(scope)
        emitAll(combine(dao.categories(scope.account,scope.profile),dao.observeTransactions(scope.account,scope.profile),dao.budgets(scope.account,scope.profile),dao.attachments(scope.account,scope.profile)) {categories,transactions,budgets,attachments->
            WorkspaceSnapshot(workspace,categories.map(CategoryEntity::toDomain),transactions.map(TransactionEntity::toDomain),budgets.map(BudgetEntity::toDomain),attachments.map(AttachmentEntity::toDomain))
        })
    }.flowOn(Dispatchers.Default)
    override fun history(scope: Scope,filter: TransactionFilter): Flow<List<FinancialTransaction>> = flow {
        validate(scope)
        require(filter.minimum>=0 && filter.maximum>=filter.minimum && filter.limit in 1..100000)
        emitAll(dao.transactions(scope.account,scope.profile,filter.range.start,filter.range.end,filter.kind,filter.category,filter.search,filter.minimum,filter.maximum,filter.sort,filter.limit).map {list->list.map(TransactionEntity::toDomain)})
    }.flowOn(Dispatchers.Default)
    private fun defaults(profile: ProfileEntity): List<CategoryEntity> {
        val expense=listOf("Food & Dining","Groceries","Shopping","Transport","Bills & Utilities","Rent","Education","Healthcare","Entertainment","Travel","Subscriptions","Gifts","Personal Care","Family","Other")
        val income=listOf("Salary","Freelancing","Business","Gift","Refund","Other")
        return listOf("EXPENSE" to expense,"INCOME" to income).flatMap { (kind,names)->names.mapIndexed { index,name->CategoryEntity(profile.accountId,profile.id,newId(),name,kind,listOf(0xFF047857,0xFF1D4ED8,0xFF7E22CE,0xFFB45309)[index%4],if(kind=="INCOME")"Income" else listOf("Food","Shopping","Transport","Home")[index%4]) } }
    }
}
