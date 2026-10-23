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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*

@Composable
fun CategoriesScreen(data: WorkspaceData, vm: AppViewModel, hidden: Boolean = false) {
    var form by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var remove by remember { mutableStateOf<Category?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader(eyebrow = "Organize", title = "Categories", subtitle = "Make your spending easier to understand.", action = { FilledTonalIconButton(enabled = !hidden, onClick = { edit = null; form = true }) { Icon(Icons.Outlined.Add, "Create category") } }) }
        item { PrivacyNotice(hidden) }
        items(data.categories, key = { it.id }) { category ->
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(if (hidden) MaterialTheme.colorScheme.surfaceVariant else Color(category.color), androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(if (hidden) Icons.Outlined.Lock else categoryIcon(category.icon), null, tint = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(if (hidden) "Private category" else category.name, style = MaterialTheme.typography.titleMedium); Text(if (hidden) "Unlock to view category details" else "${category.kind.lowercase().replaceFirstChar(Char::uppercase)} · ${if (category.custom) "Custom" else "Default"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (!hidden && category.custom) {
                        IconButton(onClick = { edit = category; form = true }) { Icon(Icons.Outlined.Edit, "Edit category") }
                        IconButton(onClick = { remove = category }) { Icon(Icons.Outlined.DeleteOutline, "Delete category", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (form && !hidden) CategoryForm(data, vm, edit) { form = false }
    remove?.let { category ->
        var replacement by remember(category.id) { mutableStateOf("") }
        val busy by vm.busy.collectAsState()
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("Reassign & delete ${category.name}?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Transactions and budgets will move to the replacement category."); Choice("Replacement category", replacement, data.categories.filter { it.id != category.id && it.kind == category.kind }.map { it.id to it.name }) { replacement = it } } }, confirmButton = { TextButton(enabled = !busy && replacement.isNotBlank(), onClick = { vm.perform("Category reassigned and deleted") { vm.finance.deleteCategory(data.workspace.scope, category.id, replacement); remove = null } }) { Text("Reassign & delete") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } })
    }
}

@Composable
private fun CategoryForm(data: WorkspaceData, vm: AppViewModel, original: Category?, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var kind by remember { mutableStateOf(original?.kind ?: "EXPENSE") }
    var icon by remember { mutableStateOf(original?.icon ?: "Category") }
    var color by remember { mutableStateOf(original?.color ?: colorChoices.first()) }
    val busy by vm.busy.collectAsState()
    var submitted by remember { mutableStateOf(false) }
    val nameError = validationError { Validation.name(name) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (original == null) "New category" else "Edit category") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormField("Category name", name, { name = it }, error = if (submitted) nameError else null)
        if (original == null) Choice("Type", kind, listOf("EXPENSE" to "Expense", "INCOME" to "Income")) { kind = it }
        Choice("Icon", icon, iconNames.map { it to it }) { icon = it }
        Choice("Color", color.toString(), colorChoices.mapIndexed { i, c -> c.toString() to listOf("Emerald", "Blue", "Violet", "Amber", "Coral", "Navy")[i] }) { color = it.toLong() }
    } }, confirmButton = { TextButton(enabled = !busy, onClick = { submitted = true; if (nameError == null) vm.perform("Category saved") { vm.finance.saveCategory(data.workspace.scope, original?.id, name, kind, color, icon); dismiss() } }) { Text("Save category") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
