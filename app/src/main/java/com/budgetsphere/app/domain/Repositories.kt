package com.budgetsphere.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface AccountStore {
    val session: StateFlow<LocalAccount?>
    fun requireAccount(): LocalAccount
    suspend fun signup(name: String,identifier: String,password: CharArray,confirm: CharArray)
    suspend fun login(identifier: String,password: CharArray)
    suspend fun verify(password: CharArray): Boolean
    suspend fun changePassword(old: CharArray,next: CharArray,confirm: CharArray)
    suspend fun biometricAccount(identifier: String): LocalAccount?
    suspend fun enableBiometric(accountId: String,ciphertext: ByteArray,iv: ByteArray)
    suspend fun disableBiometric()
    suspend fun biometricLogin(account: LocalAccount,decrypted: ByteArray)
    fun logout()
}
interface FinanceStore {
    val workspace: StateFlow<Workspace?>
    fun clearWorkspace()
    fun profiles(): Flow<List<Profile>>
    suspend fun restoreLastProfile()
    suspend fun select(profileId: String)
    fun observeWorkspace(workspace: Workspace): Flow<WorkspaceSnapshot>
    fun history(scope: Scope,filter: TransactionFilter): Flow<List<FinancialTransaction>>
    suspend fun saveProfile(id: String?,name: String,description: String,currency: String,color: Long,icon: String): Profile
    suspend fun deleteProfile(id: String)
    suspend fun saveTransaction(scope: Scope,original: FinancialTransaction?,title: String,amount: String,kind: String,category: String,date: LocalDate,description: String,notes: String,payment: String,tags: String): String
    suspend fun deleteTransaction(scope: Scope,id: String)
    suspend fun saveCategory(scope: Scope,id: String?,name: String,kind: String,color: Long,icon: String)
    suspend fun deleteCategory(scope: Scope,id: String,reassignTo: String)
    suspend fun saveBudget(scope: Scope,id: String?,category: String?,month: String,amount: String)
    suspend fun deleteBudget(scope: Scope,id: String)
    suspend fun deleteCurrentAccount()
}
