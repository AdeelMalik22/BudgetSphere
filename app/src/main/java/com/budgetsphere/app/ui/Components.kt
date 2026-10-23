package com.budgetsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.domain.Money

fun categoryIcon(name: String): ImageVector = when (name) {
    "Person" -> Icons.Outlined.Person
    "Home" -> Icons.Outlined.Home
    "Work" -> Icons.Outlined.Work
    "Travel" -> Icons.Outlined.Flight
    "Food" -> Icons.Outlined.Restaurant
    "Shopping" -> Icons.Outlined.ShoppingBag
    "Transport" -> Icons.Outlined.DirectionsBus
    "Income" -> Icons.Outlined.SouthWest
    else -> Icons.Outlined.Category
}

val iconNames = listOf("Person", "Home", "Work", "Travel", "Food", "Shopping", "Transport", "Income", "Category")
val colorChoices = listOf(0xFF1F9D78, 0xFF4169C5, 0xFF7668D6, 0xFFCC843B, 0xFFE56B58, 0xFF18233A)

@Composable
fun BrandMark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 34.dp else 48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (compact) 18.dp else 26.dp))
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text("BudgetSphere", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScreenHeader(eyebrow: String? = null, title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun MoneyText(amount: Long, currency: String, hidden: Boolean, large: Boolean = false) {
    Text(
        if (hidden) "••••••" else Money.format(amount, currency),
        style = if (large) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleMedium,
        fontWeight = if (large) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier.semantics { contentDescription = if (hidden) "Amount hidden" else "Financial amount" },
    )
}

@Composable
fun FormField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, numeric: Boolean = false, multiline: Boolean = false, error: String? = null, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            autoCorrectEnabled = !password,
        ),
    )
}

@Composable
fun Choice(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
                Text(options.find { it.first == value }?.second ?: "Choose $label", modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose $label")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, leadingIcon = { if (key == value) Icon(Icons.Outlined.Check, null) }, onClick = { onChange(key); expanded = false })
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun MetricTile(label: String, amount: Long, currency: String, hidden: Boolean, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.small).background(tint.copy(alpha = .09f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(amount, currency, hidden)
    }
}

@Composable
fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary, icon: ImageVector? = null) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        icon?.let { Icon(it, null, modifier = Modifier.size(14.dp), tint = color) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Outlined.AutoAwesome) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PrivacyNotice(hidden: Boolean) {
    if (hidden) Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary)
        Text("Private view · unlock to reveal financial details", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun validationError(check: () -> Any?): String? = try { check(); null } catch (e: Exception) { e.message ?: "Check this value" }
