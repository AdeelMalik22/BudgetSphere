package com.budgetsphere.app.ui

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import kotlinx.coroutines.flow.map

@Composable fun SettingsScreen(data: WorkspaceData,vm: AppViewModel,biometrics: Biometrics,onNavigate: (String)->Unit,onEnableBiometric: ()->Unit,onDeleteAccount: ()->Unit,onDeleteProfile: ()->Unit) {
    val prefs by vm.preferences.collectAsState();val account by vm.session.collectAsState()
    var change by remember {mutableStateOf(false)};var logout by remember {mutableStateOf(false)}
    val context=LocalContext.current
    var requestedMonthly by remember {mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)scheduleReminder(context,requestedMonthly,true)else vm.notify("Notifications were not enabled. You can change this in Android settings.")}
    val dailyFlow=remember {WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("daily-expense").map {list->list.any {!it.state.isFinished}}}
    val monthlyFlow=remember {WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("monthly-review").map {list->list.any {!it.state.isFinished}}}
    val daily by dailyFlow.collectAsState(false);val monthly by monthlyFlow.collectAsState(false)
    fun reminder(month: Boolean,enabled: Boolean) {if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestedMonthly=month;permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else scheduleReminder(context,month,enabled)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Settings",style=MaterialTheme.typography.headlineMedium)}
        item {SectionCard("Account") {Text(account?.name?:"");Text(account?.identifier?:"");TextButton(onClick={change=true}){Text("Change password")};TextButton(onClick={logout=true}){Text("Log out")}}}
        item {SectionCard("Workspace") {Text(data.workspace.profile.name);Text("Currency: ${data.workspace.profile.currency}");TextButton(onClick={onNavigate("Profiles")}){Text("Manage profiles & currency")};TextButton(onClick={onNavigate("Categories")}){Text("Manage categories")}}}
        item {SectionCard("Security & privacy") {
            Text(biometrics.availability()?:"Strong biometrics are available on this device.")
            SwitchRow("Biometric unlock",account?.biometricCipher!=null){enabled->if(enabled)onEnableBiometric()else vm.perform("Biometrics disabled"){account?.let {biometrics.remove(it.id)};vm.accounts.disableBiometric()}}
            SwitchRow("Hide values on background",prefs.autoHide){value->vm.perform{vm.settings.set("autoHide",value)}}
            SwitchRow("Screenshot protection",prefs.screenshotProtection){value->vm.perform{vm.settings.set("screenshots",value)}}
            Text("Values are always hidden after login and profile changes. Reveal requires authentication. Hidden values always enable screenshot protection.",style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard("Appearance & preferences") {
            Choice("Theme",prefs.theme,listOf("System","Light","Dark").map {it to it},{vm.perform{vm.settings.set("theme",it)}})
            SwitchRow("Week starts on Monday",prefs.mondayFirst){value->vm.perform{vm.settings.set("monday",value)}}
            Choice("Date format",prefs.dateFormat,listOf("dd MMM yyyy","yyyy-MM-dd","MM/dd/yyyy").map {it to it},{vm.perform{vm.settings.set("dateFormat",it)}})
        }}
        item {SectionCard("Local reminders") {SwitchRow("Daily expense reminder",daily){reminder(false,it)};SwitchRow("Monthly budget review",monthly){reminder(true,it)};Text("Android schedules reminders approximately. Monthly reviews are checked daily and shown on the first day. Notifications never contain amounts.",style=MaterialTheme.typography.bodySmall)}}
        item {SectionCard("Your data") {TextButton(onClick={onNavigate("Portability")}){Text("Export, encrypted backup & restore")};TextButton(onClick=onDeleteProfile){Text("Delete active profile",color=MaterialTheme.colorScheme.error)};TextButton(onClick=onDeleteAccount){Text("Delete this account and all its data",color=MaterialTheme.colorScheme.error)}}}
        item {Text("BudgetSphere works entirely offline. Accounts belong to this installation. No remote password reset is possible. Keep encrypted backups and their separate passphrases safe.",style=MaterialTheme.typography.bodySmall)}
    }
    if(change) ChangePassword(vm,biometrics,{change=false})
    if(logout) AlertDialog(onDismissRequest={logout=false},title={Text("Log out?")},text={Text("Authentication will be required to access this account again.")},confirmButton={TextButton(onClick={vm.logout();logout=false}){Text("Log out")}},dismissButton={TextButton(onClick={logout=false}){Text("Cancel")}})
}
@Composable private fun SwitchRow(label: String,value: Boolean,onChange: (Boolean)->Unit) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(label,modifier=Modifier.weight(1f));Switch(value,onChange)}}
@Composable private fun ChangePassword(vm: AppViewModel,biometrics: Biometrics,dismiss: ()->Unit) {
    var current by remember {mutableStateOf("")};var next by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")};val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest=dismiss,title={Text("Change password")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){FormField("Current password",current,{current=it},password=true);FormField("New password (12+ characters)",next,{next=it},password=true);FormField("Confirm new password",confirm,{confirm=it},password=true);Text("Biometric unlock will be disabled until you enable it again.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(enabled=!busy,onClick={vm.perform("Password changed"){val id=vm.accounts.requireAccount().id;vm.accounts.changePassword(current.toCharArray(),next.toCharArray(),confirm.toCharArray());biometrics.remove(id);current="";next="";confirm="";dismiss()}}){Text("Change password")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
