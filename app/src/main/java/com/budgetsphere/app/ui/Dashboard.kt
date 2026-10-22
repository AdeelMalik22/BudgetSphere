package com.budgetsphere.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun Dashboard(
    data: WorkspaceData,
    vm: AppViewModel,
    hidden: Boolean,
    onAdd: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val currency = data.workspace.profile.currency
    val prefs by vm.preferences.collectAsState()
    var period by remember { mutableStateOf("Month") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    val selectedRange = runCatching {
        if (period == "Custom")
            DateRange(LocalDate.parse(start).toEpochDay(), LocalDate.parse(end).toEpochDay())
        else DateRange.named(period, prefs.mondayFirst)
    }
    val selectedReport by
        produceState<FinancialReport?>(null, data.ledger, selectedRange.getOrNull()) {
            value = null
            value =
                selectedRange.getOrNull()?.let {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        data.ledger.report(it)
                    }
                }
        }
    val totals = data.ledger.total
    val month = DateRange.month()
    val spent = data.ledger.spending(YearMonth.now())
    val overall =
        data.budgets.find { it.month == YearMonth.now().toString() && it.categoryId == null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Your money, in focus", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A little clarity for every day.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Navy, contentColor = Color.White)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge)
                    MoneyText(totals.balance, currency, hidden, true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income")
                            MoneyText(totals.income, currency, hidden)
                        }
                        Column {
                            Text("Expenses")
                            MoneyText(totals.expense, currency, hidden)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAdd("EXPENSE") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Expense")
                }
                OutlinedButton(onClick = { onAdd("INCOME") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Income")
                }
            }
        }
        item {
            SectionCard("Explore a period") {
                Choice(
                    "Period",
                    period,
                    listOf(
                        "Week" to "This week",
                        "Month" to "This month",
                        "Last month" to "Last month",
                        "Year" to "This year",
                        "Custom" to "Custom range",
                    ),
                    { period = it },
                )
                if (period == "Custom") {
                    FormField("Start date (YYYY-MM-DD)", start, { start = it })
                    FormField("End date (YYYY-MM-DD)", end, { end = it })
                }
                if (selectedRange.isFailure)
                    Text("Enter a valid date range.", color = MaterialTheme.colorScheme.error)
                else if (selectedReport == null) CircularProgressIndicator()
                else {
                    Text("Income in period")
                    MoneyText(selectedReport!!.totals.income, currency, hidden)
                    Text("Expenses in period")
                    MoneyText(selectedReport!!.totals.expense, currency, hidden)
                }
            }
        }
        item {
            SectionCard("This month") {
                Text("Spending")
                MoneyText(spent, currency, hidden)
                if (overall != null) {
                    Text("Remaining monthly budget")
                    MoneyText(remaining(overall.amount, spent), currency, hidden)
                    if (!hidden)
                        LinearProgressIndicator(
                            progress = {
                                (spent.toDouble() / overall.amount).coerceIn(0.0, 1.0).toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                } else Text("Set an overall monthly budget to track what remains.")
                TextButton(onClick = { onNavigate("Budgets") }) { Text("Manage budgets") }
            }
        }
        item {
            SectionCard("Spending by category") {
                selectedRange.getOrNull()?.let { CategoryChart(data, it, hidden) }
                    ?: Text("Choose a valid date range.")
            }
        }
        item {
            Row {
                Text(
                    "Recent activity",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onNavigate("Transactions") }) { Text("View all") }
            }
        }
        if (data.transactions.isEmpty())
            item {
                EmptyState(
                    "A fresh start",
                    "Add your first expense or income. Your records will appear here.",
                )
            }
        data.transactions
            .asSequence()
            .filter { selectedRange.getOrNull()?.let { range -> it.date in range } ?: false }
            .take(5)
            .forEach { tx ->
                item(key = tx.id) {
                    TransactionRow(
                        tx,
                        data.categories.find { it.id == tx.categoryId },
                        hidden,
                        prefs.dateFormat,
                        { onOpen(tx.id) },
                    )
                }
            }
    }
}

@Composable
fun CategoryChart(data: WorkspaceData, range: DateRange, hidden: Boolean) {
    if (hidden) {
        Text("••••••", style = MaterialTheme.typography.headlineMedium)
        Text("Chart hidden for privacy")
        return
    }
    val report by
        produceState<FinancialReport?>(null, data.ledger, range) {
            value =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    data.ledger.report(range)
                }
        }
    if (report == null) {
        CircularProgressIndicator()
        return
    }
    val groups = report!!.categories.toList().sortedByDescending { it.second }
    if (groups.isEmpty()) {
        Text("No expenses in this period.")
        return
    }
    val max = groups.maxOf { it.second }.toDouble()
    groups.take(8).forEach { (id, amount) ->
        val category = data.categories.find { it.id == id }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(category?.name ?: "Category", modifier = Modifier.weight(1f))
            MoneyText(amount, data.workspace.profile.currency, false)
        }
        LinearProgressIndicator(
            progress = { (amount / max).toFloat() },
            color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AnalyticsScreen(data: WorkspaceData, vm: AppViewModel, hidden: Boolean) {
    var period by remember { mutableStateOf("Month") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    val prefs by vm.preferences.collectAsState()
    val currency = data.workspace.profile.currency
    val range = runCatching {
        if (period == "Custom")
            DateRange(LocalDate.parse(start).toEpochDay(), LocalDate.parse(end).toEpochDay())
        else DateRange.named(period, prefs.mondayFirst)
    }
    val report by
        produceState<FinancialReport?>(null, data.ledger, range.getOrNull()) {
            value =
                range.getOrNull()?.let {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        data.ledger.report(it)
                    }
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium) }
        item {
            Choice(
                "Period",
                period,
                listOf("Week", "Month", "Last month", "Year", "Custom").map { it to it },
                { period = it },
            )
        }
        if (period == "Custom")
            item {
                FormField("Start (YYYY-MM-DD)", start, { start = it })
                FormField("End (YYYY-MM-DD)", end, { end = it })
            }
        if (range.isFailure)
            item { Text("Enter a valid date range.", color = MaterialTheme.colorScheme.error) }
        else {
            val income = report?.totals?.income ?: 0
            val expenses = report?.totals?.expense ?: 0
            item {
                SectionCard("Income vs expenses") {
                    Text("Income")
                    MoneyText(income, currency, hidden)
                    Text("Expenses")
                    MoneyText(expenses, currency, hidden)
                    if (!hidden) {
                        val largest = maxOf(income, expenses, 1).toDouble()
                        LinearProgressIndicator(
                            progress = { (income / largest).toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinearProgressIndicator(
                            progress = { (expenses / largest).toFloat() },
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                SectionCard("Category distribution") {
                    CategoryChart(data, range.getOrThrow(), hidden)
                }
            }
            item {
                SectionCard("Monthly trend · up to 12 months in range") {
                    if (hidden) {
                        Text("••••••")
                        Text("Chart hidden for privacy")
                    } else {
                        val selectedRange = range.getOrThrow()
                        val firstMonth = YearMonth.from(LocalDate.ofEpochDay(selectedRange.start))
                        val lastMonth = YearMonth.from(LocalDate.ofEpochDay(selectedRange.end))
                        val count =
                            minOf(
                                    12L,
                                    java.time.temporal.ChronoUnit.MONTHS.between(
                                        firstMonth,
                                        lastMonth,
                                    ) + 1,
                                )
                                .toInt()
                        val months = (count - 1 downTo 0).map { lastMonth.minusMonths(it.toLong()) }
                        val values = months.map { month -> report?.months?.get(month) ?: 0L }
                        val color = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                            val max = values.maxOrNull()?.coerceAtLeast(1)?.toDouble() ?: 1.0
                            val width = size.width / months.size
                            values.forEachIndexed { index, value ->
                                drawLine(
                                    color,
                                    Offset(width * (index + .5f), size.height),
                                    Offset(
                                        width * (index + .5f),
                                        size.height - (value / max * size.height).toFloat(),
                                    ),
                                    strokeWidth = width * .55f,
                                )
                            }
                        }
                        months.forEachIndexed { i, month ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(month.toString())
                                MoneyText(values[i], currency, false)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("Budget vs actual — this month") {
                    val month = YearMonth.now().toString()
                    val monthRange = DateRange.month()
                    data.budgets
                        .filter { it.month == month }
                        .forEach { budget ->
                            val spent =
                                data.ledger.spending(
                                    YearMonth.parse(budget.month),
                                    budget.categoryId,
                                )
                            Text(
                                budget.categoryId?.let { id ->
                                    data.categories.find { it.id == id }?.name
                                } ?: "Overall budget"
                            )
                            Text("Budget")
                            MoneyText(budget.amount, currency, hidden)
                            Text("Actual")
                            MoneyText(spent, currency, hidden)
                        }
                    if (data.budgets.none { it.month == month })
                        Text("No budgets set for this month.")
                }
            }
        }
    }
}
