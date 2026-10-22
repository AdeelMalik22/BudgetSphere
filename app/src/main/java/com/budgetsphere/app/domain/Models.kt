package com.budgetsphere.app.domain

data class LocalAccount(
    val id: String,
    val name: String,
    val identifier: String,
    val biometricCipher: ByteArray? = null,
    val biometricIv: ByteArray? = null,
)

data class Profile(
    val accountId: String,
    val id: String,
    val name: String,
    val description: String,
    val currency: String,
    val color: Long,
    val icon: String,
)

data class Category(
    val accountId: String,
    val profileId: String,
    val id: String,
    val name: String,
    val kind: String,
    val color: Long,
    val icon: String,
    val custom: Boolean,
)

data class FinancialTransaction(
    val accountId: String,
    val profileId: String,
    val id: String,
    val title: String,
    val amount: Long,
    val currency: String,
    val kind: String,
    val categoryId: String,
    val date: Long,
    val description: String,
    val notes: String,
    val payment: String,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Budget(
    val accountId: String,
    val profileId: String,
    val id: String,
    val categoryId: String?,
    val categoryKey: String,
    val month: String,
    val amount: Long,
)

data class Attachment(
    val accountId: String,
    val profileId: String,
    val id: String,
    val transactionId: String,
    val filename: String,
    val thumbnail: String,
)

data class Workspace(val scope: Scope, val profile: Profile)

data class WorkspaceSnapshot(
    val workspace: Workspace,
    val categories: List<Category>,
    val transactions: List<FinancialTransaction>,
    val budgets: List<Budget>,
    val attachments: List<Attachment>,
) {
    val ledger =
        Ledger(
            transactions.map { LedgerEntry(it.amount, it.kind == "INCOME", it.categoryId, it.date) }
        )
}

data class TransactionFilter(
    val range: DateRange,
    val kind: String = "",
    val category: String = "",
    val search: String = "",
    val minimum: Long = 0,
    val maximum: Long = Long.MAX_VALUE,
    val sort: String = "newest",
    val limit: Int = 100,
)
