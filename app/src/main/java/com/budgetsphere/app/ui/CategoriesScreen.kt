package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*

@Composable fun CategoriesScreen(data: WorkspaceData,vm: AppViewModel) {
    var form by remember {mutableStateOf(false)};var edit by remember {mutableStateOf<Category?>(null)};var remove by remember {mutableStateOf<Category?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("Categories",style=MaterialTheme.typography.headlineMedium);Button(onClick={edit=null;form=true}){Text("Create category")}}
        items(data.categories,key={it.id}) {category->ListItem(headlineContent={Text(category.name)},supportingContent={Text("${category.kind.lowercase()} · ${if(category.custom)"Custom" else "Default"}")},leadingContent={Icon(categoryIcon(category.icon),null,tint=Color(category.color))},trailingContent={if(category.custom)Row{TextButton(onClick={edit=category;form=true}){Text("Edit")};TextButton(onClick={remove=category}){Text("Delete")}}})}
    }
    if(form) CategoryForm(data,vm,edit,{form=false})
    remove?.let {category->
        var replacement by remember(category.id){mutableStateOf("")}
        val busy by vm.busy.collectAsState()
        AlertDialog(onDismissRequest={remove=null},title={Text("Reassign & delete ${category.name}?")},text={Column{Text("All transactions and budgets will move to the category you choose. No financial records will be deleted.");Choice("Replacement category",replacement,data.categories.filter {it.id!=category.id && it.kind==category.kind}.map {it.id to it.name},{replacement=it})}},confirmButton={TextButton(enabled=!busy && replacement.isNotBlank(),onClick={vm.perform("Category reassigned and deleted"){vm.finance.deleteCategory(data.workspace.scope,category.id,replacement);remove=null}}){Text("Reassign & delete")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})
    }
}
@Composable private fun CategoryForm(data: WorkspaceData,vm: AppViewModel,original: Category?,dismiss: ()->Unit) {
    var name by remember {mutableStateOf(original?.name?:"")};var kind by remember {mutableStateOf(original?.kind?:"EXPENSE")};var icon by remember {mutableStateOf(original?.icon?:"Category")};var color by remember {mutableStateOf(original?.color?:colorChoices.first())};val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest=dismiss,title={Text(if(original==null)"New category" else "Edit category")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        FormField("Category name",name,{name=it})
        if(original==null)Choice("Type",kind,listOf("EXPENSE" to "Expense","INCOME" to "Income"),{kind=it})
        Choice("Icon",icon,iconNames.map {it to it},{icon=it})
        Choice("Color",color.toString(),colorChoices.mapIndexed {i,c->c.toString() to listOf("Emerald","Blue","Violet","Amber","Rose","Navy")[i]},{color=it.toLong()})
    }},confirmButton={TextButton(enabled=!busy,onClick={vm.perform("Category saved"){vm.finance.saveCategory(data.workspace.scope,original?.id,name,kind,color,icon);dismiss()}}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
