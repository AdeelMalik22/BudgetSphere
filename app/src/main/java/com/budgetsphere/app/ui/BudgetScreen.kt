package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import com.budgetsphere.app.domain.*
import java.time.YearMonth

@Composable fun BudgetsScreen(data: WorkspaceData,vm: AppViewModel,hidden: Boolean) {
    var form by remember {mutableStateOf(false)};var edit by remember {mutableStateOf<Budget?>(null)};var delete by remember {mutableStateOf<Budget?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Budgets",style=MaterialTheme.typography.headlineMedium);Text("Make room for what matters.");Button(enabled=!hidden,onClick={edit=null;form=true}){Text("Create budget")}}
        if(hidden)item{Text("Unlock using the eye control to enter or edit budget amounts.")}
        if(data.budgets.isEmpty()) item {EmptyState("Give your month a plan","Set an overall budget or a limit for a spending category.")}
        items(data.budgets,key={it.id}) {budget->
            val month=YearMonth.parse(budget.month);val range=DateRange(month.atDay(1).toEpochDay(),month.atEndOfMonth().toEpochDay())
            val spent=data.ledger.spending(month,budget.categoryId)
            SectionCard(budget.categoryId?.let {id->data.categories.find {it.id==id}?.name}?:"Overall monthly budget") {
                Text(budget.month);Text("Budget");MoneyText(budget.amount,data.workspace.profile.currency,hidden);Text("Spent");MoneyText(spent,data.workspace.profile.currency,hidden);Text("Remaining");MoneyText(remaining(budget.amount,spent),data.workspace.profile.currency,hidden)
                if(!hidden){LinearProgressIndicator(progress={(spent.toDouble()/budget.amount).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth(),color=if(spent>budget.amount)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);if(spent>=budget.amount)Text("Budget limit reached",color=MaterialTheme.colorScheme.error)else if(spent.toDouble()/budget.amount>=.8)Text("Approaching your budget limit")}
                Row {TextButton(enabled=!hidden,onClick={edit=budget;form=true}){Text("Edit")};TextButton(onClick={delete=budget}){Text("Delete")}}
            }
        }
    }
    if(form) BudgetForm(data,vm,edit,{form=false})
    delete?.let {budget->ConfirmDialog("Delete budget?","Transactions will be kept.",{delete=null},{vm.perform("Budget deleted"){vm.finance.deleteBudget(data.workspace.scope,budget.id)};delete=null})}
}
@Composable private fun BudgetForm(data: WorkspaceData,vm: AppViewModel,original: Budget?,dismiss: ()->Unit) {
    var category by remember {mutableStateOf(original?.categoryId?:"")};var month by remember {mutableStateOf(original?.month?:YearMonth.now().toString())};var amount by remember {mutableStateOf(original?.let {Money.decimal(it.amount,data.workspace.profile.currency)}?:"")}
    val hidden by vm.hidden.collectAsState();val busy by vm.busy.collectAsState()
    var submitted by remember {mutableStateOf(false)}
    val amountError=validationError{Money.parse(amount,data.workspace.profile.currency)}
    val monthError=validationError{require(runCatching {YearMonth.parse(month).year in 1900..2200}.getOrDefault(false)){"Use YYYY-MM with a year between 1900 and 2200"}}
    if(hidden){LaunchedEffect(Unit){dismiss()};return}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(original==null)"New budget" else "Edit budget")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Choice("Category",category,listOf("" to "Overall monthly budget")+data.categories.filter {it.kind=="EXPENSE"}.map {it.id to it.name},{category=it})
        FormField("Month (YYYY-MM)",month,{month=it},error=if(submitted)monthError else null);FormField("Amount (${data.workspace.profile.currency})",amount,{amount=it},numeric=true,error=if(submitted)amountError else null)
    }},confirmButton={TextButton(enabled=!busy,onClick={submitted=true;if(amountError==null && monthError==null)vm.perform("Budget saved"){vm.finance.saveBudget(data.workspace.scope,original?.id,category.ifBlank {null},month,amount);dismiss()}}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
