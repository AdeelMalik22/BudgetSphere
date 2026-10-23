package com.budgetsphere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import kotlinx.coroutines.flow.map

@Composable
fun SettingsScreen(data: WorkspaceData, vm: AppViewModel, biometrics: Biometrics, onNavigate: (String) -> Unit, onEnableBiometric: () -> Unit, onDeleteAccount: () -> Unit, onDeleteProfile: () -> Unit) {
    val prefs by vm.preferences.collectAsState()
    val account by vm.session.collectAsState()
    var change by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var requestedMonthly by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scheduleReminder(context, requestedMonthly, true) else vm.notify("Notifications were not enabled. You can change this in Android settings.") }
    val dailyFlow = remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("daily-expense").map { list -> list.any { !it.state.isFinished } } }
    val monthlyFlow = remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("monthly-review").map { list -> list.any { !it.state.isFinished } } }
    val daily by dailyFlow.collectAsState(false)
    val monthly by monthlyFlow.collectAsState(false)
    fun reminder(month: Boolean, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { requestedMonthly = month; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else scheduleReminder(context, month, enabled)
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader(eyebrow = "Preferences", title = "Settings", subtitle = "Make BudgetSphere feel like your own space.") }
        item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondary, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSecondary) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(account?.name ?: "", style = MaterialTheme.typography.titleMedium); Text(account?.identifier ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { change = true }) { Icon(Icons.Outlined.Key, "Change password") }
                }
            }
        }
        item { SettingsGroup("Workspace", Icons.Outlined.Folder) { SettingsAction("Manage profiles & currency", data.workspace.profile.name, Icons.Outlined.SwapHoriz) { onNavigate("Profiles") }; SettingsAction("Categories", "Organize your spending labels", Icons.Outlined.Category) { onNavigate("Categories") } } }
        item {
            SettingsGroup("Security & privacy", Icons.Outlined.Shield) {
                Text(biometrics.availability() ?: "Strong biometrics are available on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Biometric unlock", account?.biometricCipher != null) { enabled -> if (enabled) onEnableBiometric() else vm.perform("Biometrics disabled") { account?.let { biometrics.remove(it.id) }; vm.accounts.disableBiometric() } }
                SwitchRow("Hide values on background", prefs.autoHide) { vm.perform { vm.settings.set("autoHide", it) } }
                SwitchRow("Screenshot protection", prefs.screenshotProtection) { vm.perform { vm.settings.set("screenshots", it) } }
                Text("Values are hidden after login and profile changes. Reveal always requires authentication.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup("Appearance", Icons.Outlined.Palette) {
                Choice("Theme", prefs.theme, listOf("System", "Light", "Dark").map { it to it }) { vm.perform { vm.settings.set("theme", it) } }
                SwitchRow("Week starts on Monday", prefs.mondayFirst) { vm.perform { vm.settings.set("monday", it) } }
                Choice("Date format", prefs.dateFormat, listOf("dd MMM yyyy", "yyyy-MM-dd", "MM/dd/yyyy").map { it to it }) { vm.perform { vm.settings.set("dateFormat", it) } }
            }
        }
        item {
            SettingsGroup("Local reminders", Icons.Outlined.NotificationsNone) {
                SwitchRow("Daily expense reminder", daily) { reminder(false, it) }
                SwitchRow("Monthly budget review", monthly) { reminder(true, it) }
                Text("Android schedules reminders approximately. Notifications never contain amounts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup("Your data", Icons.Outlined.FolderOpen) {
                SettingsAction("Export & encrypted backup", "Move your data safely", Icons.Outlined.ImportExport) { onNavigate("Portability") }
                SettingsAction("Delete active workspace", "Permanent and cannot be undone", Icons.Outlined.DeleteOutline, MaterialTheme.colorScheme.error) { onDeleteProfile() }
                SettingsAction("Delete local account", "Remove all data from this device", Icons.Outlined.DeleteForever, MaterialTheme.colorScheme.error) { onDeleteAccount() }
            }
        }
        item { Text("BudgetSphere works entirely offline. Keep encrypted backups and their separate passphrases safe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton(onClick = { logout = true }, modifier = Modifier.fillMaxWidth()) { Text("Log out") } }
    }
    if (change) ChangePassword(vm, biometrics) { change = false }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Log out?") }, text = { Text("Authentication will be required to access this account again.") }, confirmButton = { TextButton(onClick = { vm.logout(); logout = false }) { Text("Log out") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)); Text(title, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = value, onCheckedChange = onChange) }
}

@Composable
private fun ChangePassword(vm: AppViewModel, biometrics: Biometrics, dismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest = dismiss, title = { Text("Change password") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormField("Current password", current, { current = it }, password = true)
        FormField("New password (12+ characters)", next, { next = it }, password = true)
        FormField("Confirm new password", confirm, { confirm = it }, password = true)
        Text("Biometric unlock will be disabled until you enable it again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, confirmButton = { TextButton(enabled = !busy, onClick = { vm.perform("Password changed") { val id = vm.accounts.requireAccount().id; vm.accounts.changePassword(current.toCharArray(), next.toCharArray(), confirm.toCharArray()); biometrics.remove(id); current = ""; next = ""; confirm = ""; dismiss() } }) { Text("Change password") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
