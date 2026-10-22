package com.budgetsphere.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun TransactionRow(tx: TransactionEntity,category: CategoryEntity?,hidden: Boolean,format: String,onClick: ()->Unit) {
    ListItem(modifier=Modifier.clickable(onClick=onClick),headlineContent={Text(tx.title)},supportingContent={Text("${category?.name?:"Category"} · ${LocalDate.ofEpochDay(tx.date).format(DateTimeFormatter.ofPattern(format))}")},leadingContent={Icon(if(tx.kind=="INCOME")Icons.Outlined.SouthWest else categoryIcon(category?.icon?:"Category"),null,tint=if(tx.kind=="INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)},trailingContent={MoneyText(if(tx.kind=="INCOME")tx.amount else -tx.amount,tx.currency,hidden)})
}
@Composable fun TransactionsScreen(data: WorkspaceData,vm: AppViewModel,hidden: Boolean,onOpen: (String)->Unit,onAdd: ()->Unit) {
    var search by remember {mutableStateOf("")};var category by remember {mutableStateOf("")};var kind by remember {mutableStateOf("")};var period by remember {mutableStateOf("All")};var sort by remember {mutableStateOf("newest")};var start by remember {mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())};var end by remember {mutableStateOf(LocalDate.now().toString())};var minimum by remember {mutableStateOf("")};var maximum by remember {mutableStateOf("")};var limit by remember {mutableIntStateOf(100)};var filters by remember {mutableStateOf(false)}
    val prefs by vm.preferences.collectAsState()
    val range=runCatching { if(period=="All") DateRange(Long.MIN_VALUE,Long.MAX_VALUE) else if(period=="Custom") DateRange(LocalDate.parse(start).toEpochDay(),LocalDate.parse(end).toEpochDay()) else DateRange.named(period,prefs.mondayFirst) }
    val lower=runCatching {if(minimum.isBlank())0L else Money.parse(minimum,data.workspace.profile.currency)}
    val upper=runCatching {if(maximum.isBlank())Long.MAX_VALUE else Money.parse(maximum,data.workspace.profile.currency)}
    val invalid=range.isFailure || lower.isFailure || upper.isFailure || (lower.getOrDefault(0)>upper.getOrDefault(Long.MAX_VALUE))
    val scope=data.workspace.scope
    val flow=remember(scope,search,category,kind,period,start,end,minimum,maximum,sort,limit,invalid) {
        if(invalid) kotlinx.coroutines.flow.flowOf(emptyList()) else vm.finance.dao.transactions(scope.account,scope.profile,range.getOrThrow().start,range.getOrThrow().end,kind,category,search,lower.getOrThrow(),upper.getOrThrow(),sort,limit)
    }
    val rows by flow.collectAsState(initial=emptyList())
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Row {Text("Transactions",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));IconButton(onClick=onAdd){Icon(Icons.Outlined.Add,"Add transaction")}}}
        item {FormField("Search title or tags",search,{search=it;limit=100})}
        item {TextButton(onClick={filters=!filters}){Icon(Icons.Outlined.FilterList,null);Text(if(filters)"Hide filters" else "Filter & sort")}}
        if(filters) item {SectionCard("Find transactions") {
            Choice("Type",kind,listOf("" to "All","EXPENSE" to "Expenses","INCOME" to "Income"),{kind=it})
            Choice("Category",category,listOf("" to "All categories")+data.categories.map {it.id to it.name},{category=it})
            Choice("Period",period,listOf("All","Week","Month","Last month","Year","Custom").map {it to it},{period=it})
            if(period=="Custom") {FormField("Start date (YYYY-MM-DD)",start,{start=it});FormField("End date (YYYY-MM-DD)",end,{end=it})}
            if(!hidden) {FormField("Minimum amount (optional)",minimum,{minimum=it},numeric=true);FormField("Maximum amount (optional)",maximum,{maximum=it},numeric=true)}
            Choice("Sort",sort,listOf("newest" to "Newest first","oldest" to "Oldest first","amount" to "Largest amount"),{sort=it})
        }}
        if(invalid) item {Text("Check date and amount ranges.",color=MaterialTheme.colorScheme.error)}
        else if(rows.isEmpty()) item {EmptyState("No matching transactions","Add your first transaction or adjust the filters.")}
        var previousDate: Long?=null
        rows.forEach { tx->
            if(previousDate!=tx.date) item(key="date-${tx.date}-${tx.id}"){Text(LocalDate.ofEpochDay(tx.date).format(DateTimeFormatter.ofPattern(prefs.dateFormat)),style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=12.dp))}
            item(key=tx.id){TransactionRow(tx,data.categories.find {it.id==tx.categoryId},hidden,prefs.dateFormat,{onOpen(tx.id)})}
            previousDate=tx.date
        }
        if(rows.size>=limit) item {TextButton(onClick={limit+=100}){Text("Load more")}}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TransactionForm(data: WorkspaceData,vm: AppViewModel,original: TransactionEntity?,initialKind: String,onSaved: (String)->Unit) {
    val scope=data.workspace.scope
    var title by remember {mutableStateOf(original?.title?:"")};var amount by remember {mutableStateOf(original?.let {Money.decimal(it.amount,it.currency)}?:"")};var kind by remember {mutableStateOf(original?.kind?:initialKind)};var category by remember {mutableStateOf(original?.categoryId?:"")};var date by remember {mutableStateOf(original?.let {LocalDate.ofEpochDay(it.date).toString()}?:LocalDate.now().toString())};var description by remember {mutableStateOf(original?.description?:"")};var notes by remember {mutableStateOf(original?.notes?:"")};var payment by remember {mutableStateOf(original?.payment?:"")};var tags by remember {mutableStateOf(original?.tags?:"")};var picker by remember {mutableStateOf(false)}
    val busy by vm.busy.collectAsState();val hidden by vm.hidden.collectAsState()
    if(hidden) {EmptyState("Unlock financial information","Use the eye control to authenticate before entering or editing amounts.");return}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(if(original==null)"New transaction" else "Edit transaction",style=MaterialTheme.typography.headlineMedium)}
        item {Choice("Type",kind,listOf("EXPENSE" to "Expense","INCOME" to "Income"),{kind=it;category=""})}
        item {FormField("Amount (${data.workspace.profile.currency})",amount,{amount=it},numeric=true)}
        item {FormField(if(kind=="INCOME")"Income source" else "Expense title",title,{title=it})}
        item {Choice("Category",category,data.categories.filter {it.kind==kind}.map {it.id to it.name},{category=it})}
        item {OutlinedButton(onClick={picker=true}){Icon(Icons.Outlined.CalendarMonth,null);Text(date)}}
        item {FormField("Description (optional)",description,{description=it},multiline=true)}
        item {FormField("Notes (optional)",notes,{notes=it},multiline=true)}
        item {FormField("Payment method (optional)",payment,{payment=it})}
        item {FormField("Tags (comma-separated, optional)",tags,{tags=it})}
        item {Text("Save the transaction to add or manage its receipts.",style=MaterialTheme.typography.bodySmall)}
        item {Button(enabled=!busy,onClick={vm.perform("Transaction saved") {val id=vm.finance.saveTransaction(scope,original,title,amount,kind,category,LocalDate.parse(date),description,notes,payment,tags);onSaved(id)}},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Saving…" else "Save transaction")}}
    }
    if(picker) {
        val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(date).toEpochDay()*86400000L,yearRange=1900..2200)
        DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let {date=LocalDate.ofEpochDay(it/86400000L).toString()};picker=false}){Text("Choose")}},dismissButton={TextButton(onClick={picker=false}){Text("Cancel")}}){DatePicker(state)}
    }
}
@Composable fun TransactionDetails(data: WorkspaceData,tx: TransactionEntity,vm: AppViewModel,hidden: Boolean,onEdit: ()->Unit,onDeleted: ()->Unit,receipts: @Composable ()->Unit) {
    var confirm by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(tx.title,style=MaterialTheme.typography.headlineMedium);MoneyText(tx.amount,tx.currency,hidden,true)}
        item {SectionCard("Details") {
            Text("${tx.kind.lowercase().replaceFirstChar(Char::uppercase)} · ${data.categories.find {it.id==tx.categoryId}?.name?:"Category"}")
            Text(LocalDate.ofEpochDay(tx.date).toString())
            if(!hidden) {if(tx.description.isNotBlank())Text(tx.description);if(tx.notes.isNotBlank())Text(tx.notes);if(tx.payment.isNotBlank())Text("Paid via ${tx.payment}");if(tx.tags.isNotBlank())Text(tx.tags)} else Text("Unlock to view notes and receipts.")
        }}
        item {Row {OutlinedButton(onClick=onEdit,enabled=!hidden){Text("Edit")};Spacer(Modifier.width(12.dp));TextButton(onClick={confirm=true}){Text("Delete",color=MaterialTheme.colorScheme.error)}}}
        item {receipts()}
    }
    if(confirm) ConfirmDialog("Delete transaction?","This permanently deletes the transaction and its receipt references.",{confirm=false},{vm.perform("Transaction deleted"){vm.finance.deleteTransaction(data.workspace.scope,tx.id);onDeleted()};confirm=false})
}
