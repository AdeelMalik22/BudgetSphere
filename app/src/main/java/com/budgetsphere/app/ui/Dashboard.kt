package com.budgetsphere.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun Dashboard(data: WorkspaceData, vm: AppViewModel, hidden: Boolean, onAdd: (String) -> Unit, onOpen: (String) -> Unit, onNavigate: (String) -> Unit) {
    val currency = data.workspace.profile.currency
    val prefs by vm.preferences.collectAsState()
    var period by remember { mutableStateOf("Month") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    val selectedRange = runCatching {
        if (period == "Custom") DateRange(LocalDate.parse(start).toEpochDay(), LocalDate.parse(end).toEpochDay())
        else DateRange.named(period, prefs.mondayFirst)
    }
    val selectedReport by produceState<FinancialReport?>(null, data.ledger, selectedRange.getOrNull()) {
        value = selectedRange.getOrNull()?.let { range -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { data.ledger.report(range) } }
    }
    val totals = data.ledger.total
    val spent = data.ledger.spending(YearMonth.now())
    val overall = data.budgets.find { it.month == YearMonth.now().toString() && it.categoryId == null }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "${LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)} · ${LocalDate.now().month.name.lowercase().replaceFirstChar(Char::uppercase)} ${LocalDate.now().dayOfMonth}",
                title = "Your money, in focus",
                subtitle = "Small decisions add up to a clearer life.",
            )
        }
        item { BalanceHero(totals, currency, hidden) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onAdd("EXPENSE") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Icon(Icons.Outlined.Remove, null); Spacer(Modifier.width(7.dp)); Text("Expense")
                }
                Button(onClick = { onAdd("INCOME") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(7.dp)); Text("Income")
                }
            }
        }
        item { PrivacyNotice(hidden) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Overview")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Income", selectedReport?.totals?.income ?: 0, currency, hidden, Emerald, Modifier.weight(1f))
                    MetricTile("Spent", selectedReport?.totals?.expense ?: 0, currency, hidden, Coral, Modifier.weight(1f))
                }
                SingleChoiceRow(listOf("Week", "Month", "Last month", "Year", "Custom"), period) { period = it }
                if (period == "Custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField("Start", start, { start = it }, modifier = Modifier.weight(1f))
                        FormField("End", end, { end = it }, modifier = Modifier.weight(1f))
                    }
                }
                if (selectedRange.isFailure) Text("Enter a valid date range.", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            SectionHeader("This month") { TextButton(onClick = { onNavigate("Budgets") }) { Text("Budgets") } }
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Spent so far", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(spent, currency, hidden, true)
                        }
                        overall?.let {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text("of monthly limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(it.amount, currency, hidden)
                            }
                        }
                    }
                    if (overall != null && !hidden) {
                        LinearProgressIndicator({ (spent.toDouble() / overall.amount).coerceIn(0.0, 1.0).toFloat() }, Modifier.fillMaxWidth(), color = if (spent > overall.amount) Coral else Emerald)
                        Text(if (spent > overall.amount) "Over your monthly plan" else "You are on track", style = MaterialTheme.typography.labelMedium, color = if (spent > overall.amount) Coral else Emerald)
                    } else if (overall == null) Text("Set a monthly plan to see your progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionHeader("Spending by category") { TextButton(onClick = { onNavigate("Analytics") }) { Text("Details") } }
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.padding(18.dp)) { selectedRange.getOrNull()?.let { CategoryChart(data, it, hidden) } ?: Text("Choose a valid date range.") }
            }
        }
        item {
            SectionHeader("Recent activity") { TextButton(onClick = { onNavigate("Transactions") }) { Text("View all") } }
        }
        if (data.transactions.isEmpty()) item { EmptyState("A fresh start", "Add your first expense or income to see it here.", Icons.Outlined.Spa) }
        data.transactions.asSequence().filter { selectedRange.getOrNull()?.let { range -> it.date in range } ?: false }.take(5).forEach { tx ->
            item(key = tx.id) { TransactionRow(tx, data.categories.find { it.id == tx.categoryId }, hidden, prefs.dateFormat, { onOpen(tx.id) }) }
        }
    }
}

@Composable
private fun BalanceHero(totals: Totals, currency: String, hidden: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Navy,
        contentColor = Color.White,
        tonalElevation = 4.dp,
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Navy, Color(0xFF243A61))))) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Available balance", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(.72f))
                        MoneyText(totals.balance, currency, hidden, true)
                    }
                    Box(Modifier.size(44.dp).background(Color.White.copy(.12f), MaterialTheme.shapes.small), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HeroMetric("Income", totals.income, currency, hidden, Emerald)
                    HeroMetric("Expenses", totals.expense, currency, hidden, Coral)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeroMetric(label: String, amount: Long, currency: String, hidden: Boolean, color: Color) {
    Row(Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.62f))
            Text(if (hidden) "••••••" else Money.format(amount, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SingleChoiceRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(onClick = { onSelected(option) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent, tonalElevation = if (active) 2.dp else 0.dp) {
                Box(Modifier.padding(vertical = 10.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(option, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun CategoryChart(data: WorkspaceData, range: DateRange, hidden: Boolean) {
    if (hidden) { Text("••••••", style = MaterialTheme.typography.headlineMedium); Text("Chart hidden for privacy", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val report by produceState<FinancialReport?>(null, data.ledger, range) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { data.ledger.report(range) } }
    val groups = report?.categories?.toList()?.sortedByDescending { it.second } ?: emptyList()
    if (groups.isEmpty()) { Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val max = groups.maxOf { it.second }.toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.take(5).forEach { (id, amount) ->
            val category = data.categories.find { it.id == id }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(Color(category?.color ?: 0xFF1F9D78), CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(categoryIcon(category?.icon ?: "Category"), null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(category?.name ?: "Category", Modifier.weight(1f))
                    MoneyText(amount, data.workspace.profile.currency, false)
                }
                LinearProgressIndicator(progress = { (amount / max).toFloat() }, modifier = Modifier.fillMaxWidth(), color = Color(category?.color ?: 0xFF1F9D78), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
fun AnalyticsScreen(data: WorkspaceData, vm: AppViewModel, hidden: Boolean) {
    var period by remember { mutableStateOf("Month") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    val prefs by vm.preferences.collectAsState()
    val currency = data.workspace.profile.currency
    val range = runCatching { if (period == "Custom") DateRange(LocalDate.parse(start).toEpochDay(), LocalDate.parse(end).toEpochDay()) else DateRange.named(period, prefs.mondayFirst) }
    val report by produceState<FinancialReport?>(null, data.ledger, range.getOrNull()) { value = range.getOrNull()?.let { selected -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { data.ledger.report(selected) } } }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader(eyebrow = "Insights", title = "Analytics", subtitle = "Understand the patterns behind your spending.") }
        item { SingleChoiceRow(listOf("Week", "Month", "Last month", "Year", "Custom"), period) { period = it } }
        if (period == "Custom") item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FormField("Start", start, { start = it }, modifier = Modifier.weight(1f)); FormField("End", end, { end = it }, modifier = Modifier.weight(1f)) } }
        if (range.isFailure) item { Text("Enter a valid date range.", color = MaterialTheme.colorScheme.error) }
        else {
            val income = report?.totals?.income ?: 0
            val expenses = report?.totals?.expense ?: 0
            item { SectionCard("Income vs expenses") { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricTile("Income", income, currency, hidden, Emerald, Modifier.weight(1f)); MetricTile("Expenses", expenses, currency, hidden, Coral, Modifier.weight(1f)) } } }
            item { SectionCard("Category distribution") { CategoryChart(data, range.getOrThrow(), hidden) } }
            item { SectionCard("Monthly trend") { TrendChart(report, range.getOrThrow(), currency, hidden) } }
            item {
                SectionCard("Budget vs actual") {
                    val month = YearMonth.now().toString()
                    data.budgets.filter { it.month == month }.forEach { budget ->
                        val spent = data.ledger.spending(YearMonth.parse(budget.month), budget.categoryId)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(if (hidden) "Private budget" else budget.categoryId?.let { id -> data.categories.find { it.id == id }?.name } ?: "Overall budget")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Budget", color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(budget.amount, currency, hidden) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Actual", color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(spent, currency, hidden) }
                        }
                    }
                    if (data.budgets.none { it.month == month }) Text("No budgets set for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrendChart(report: FinancialReport?, range: DateRange, currency: String, hidden: Boolean) {
    if (hidden) { Text("••••••", style = MaterialTheme.typography.headlineMedium); Text("Chart hidden for privacy"); return }
    val firstMonth = YearMonth.from(LocalDate.ofEpochDay(range.start))
    val lastMonth = YearMonth.from(LocalDate.ofEpochDay(range.end))
    val count = minOf(12L, java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1).toInt()
    val months = (count - 1 downTo 0).map { lastMonth.minusMonths(it.toLong()) }
    val values = months.map { report?.months?.get(it) ?: 0L }
    val chartColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val max = values.maxOrNull()?.coerceAtLeast(1)?.toDouble() ?: 1.0
        val width = size.width / months.size
        values.forEachIndexed { index, value ->
            drawLine(chartColor, Offset(width * (index + .5f), size.height), Offset(width * (index + .5f), size.height - (value / max * size.height).toFloat()), strokeWidth = width * .55f)
        }
    }
    months.forEachIndexed { i, month -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(month.toString(), style = MaterialTheme.typography.labelMedium); MoneyText(values[i], currency, false) } }
}
