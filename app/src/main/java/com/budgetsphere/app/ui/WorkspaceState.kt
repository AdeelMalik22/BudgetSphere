package com.budgetsphere.app.ui
import com.budgetsphere.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import com.budgetsphere.app.domain.*
data class WorkspaceData(val workspace: Workspace,val categories: List<CategoryEntity>,val transactions: List<TransactionEntity>,val budgets: List<BudgetEntity>,val attachments: List<AttachmentEntity>) {
    val ledger=Ledger(transactions.map {LedgerEntry(it.amount,it.kind=="INCOME",it.categoryId,it.date)})
}
fun FinanceRepository.observe(workspace: Workspace): Flow<WorkspaceData> {
    val s=workspace.scope
    return combine(dao.categories(s.account,s.profile),dao.observeTransactions(s.account,s.profile),dao.budgets(s.account,s.profile),dao.attachments(s.account,s.profile)) { categories,transactions,budgets,attachments->WorkspaceData(workspace,categories,transactions,budgets,attachments) }.flowOn(Dispatchers.Default)
}
