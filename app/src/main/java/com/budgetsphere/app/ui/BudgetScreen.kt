package com.budgetsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import java.time.YearMonth

@Composable
fun BudgetsScreen(data: WorkspaceData, vm: AppViewModel, hidden: Boolean) {
    var form by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Budget?>(null) }
    var delete by remember { mutableStateOf<Budget?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader(eyebrow = "Planning", title = "Budgets", subtitle = "Give your future spending a little shape.", action = { FilledTonalButton(enabled = !hidden, onClick = { edit = null; form = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Create budget") } }) }
        item { PrivacyNotice(hidden) }
        if (data.budgets.isEmpty()) item { EmptyState("Make room for what matters", "Set an overall plan or a limit for a category.", Icons.Outlined.AccountBalanceWallet) }
        items(data.budgets, key = { it.id }) { budget ->
            val month = YearMonth.parse(budget.month)
            val spent = data.ledger.spending(month, budget.categoryId)
            val category = budget.categoryId?.let { id -> data.categories.find { it.id == id } }
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(categoryIcon(category?.icon ?: "Home"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (hidden) "Private budget" else category?.name ?: "Overall monthly budget", style = MaterialTheme.typography.titleMedium)
                            Text(if (hidden) "Private month" else budget.month, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!hidden) Text(if (spent > budget.amount) "Over" else "On track", style = MaterialTheme.typography.labelMedium, color = if (spent > budget.amount) Coral else Emerald)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(spent, data.workspace.profile.currency, hidden) }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Limit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(budget.amount, data.workspace.profile.currency, hidden) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(remaining(budget.amount, spent), data.workspace.profile.currency, hidden)
                    }
                    if (!hidden) LinearProgressIndicator(progress = { (spent.toDouble() / budget.amount).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = if (spent > budget.amount) Coral else Emerald, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = !hidden, onClick = { edit = budget; form = true }) { Text("Edit") }
                        TextButton(onClick = { delete = budget }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (form) BudgetForm(data, vm, edit) { form = false }
    delete?.let { budget ->
        ConfirmDialog("Delete budget?", "Transactions will be kept.", { delete = null }, { vm.perform("Budget deleted") { vm.finance.deleteBudget(data.workspace.scope, budget.id) }; delete = null })
    }
}

@Composable
private fun BudgetForm(data: WorkspaceData, vm: AppViewModel, original: Budget?, dismiss: () -> Unit) {
    var category by remember { mutableStateOf(original?.categoryId ?: "") }
    var month by remember { mutableStateOf(original?.month ?: YearMonth.now().toString()) }
    var amount by remember { mutableStateOf(original?.let { Money.decimal(it.amount, data.workspace.profile.currency) } ?: "") }
    val hidden by vm.hidden.collectAsState()
    val busy by vm.busy.collectAsState()
    var submitted by remember { mutableStateOf(false) }
    val amountError = validationError { Money.parse(amount, data.workspace.profile.currency) }
    val monthError = validationError { require(runCatching { YearMonth.parse(month).year in 1900..2200 }.getOrDefault(false)) { "Use YYYY-MM with a year between 1900 and 2200" } }
    if (hidden) { LaunchedEffect(Unit) { dismiss() }; return }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (original == null) "New budget" else "Edit budget") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Category", category, listOf("" to "Overall monthly budget") + data.categories.filter { it.kind == "EXPENSE" }.map { it.id to it.name }) { category = it }
            FormField("Month (YYYY-MM)", month, { month = it }, error = if (submitted) monthError else null)
            FormField("Amount (${data.workspace.profile.currency})", amount, { amount = it }, numeric = true, error = if (submitted) amountError else null)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { submitted = true; if (amountError == null && monthError == null) vm.perform("Budget saved") { vm.finance.saveBudget(data.workspace.scope, original?.id, category.ifBlank { null }, month, amount); dismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
