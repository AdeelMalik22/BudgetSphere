package com.budgetsphere.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.DocumentAuthorization

@Composable fun PortabilityScreen(data: WorkspaceData,vm: AppViewModel,portability: Portability,hidden: Boolean) {
    var mode by remember {mutableStateOf("")};var password by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    val busy by vm.busy.collectAsState();val scope=data.workspace.scope
    val authorization=remember(scope){DocumentAuthorization()}
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->val allowed=authorization.consume(vm.workspace.value?.scope,"CSV");if(uri!=null && allowed)vm.perform("CSV exported"){portability.export(scope,uri,true)}}
    val exportJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->val allowed=authorization.consume(vm.workspace.value?.scope,"JSON");if(uri!=null && allowed)vm.perform("JSON exported"){portability.export(scope,uri,false)}}
    val backup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->val secret=password.toCharArray();password="";confirm="";val allowed=authorization.consume(vm.workspace.value?.scope,"Backup");if(uri!=null && allowed)vm.perform("Encrypted backup saved"){portability.backup(scope.account,uri,secret)}else secret.fill('\u0000')}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->val secret=password.toCharArray();password="";confirm="";val allowed=authorization.consume(vm.workspace.value?.scope,"Restore");if(uri!=null && allowed)vm.perform("Backup restored into new profiles"){portability.restore(scope.account,uri,secret)}else secret.fill('\u0000')}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Keep your data yours",style=MaterialTheme.typography.headlineMedium)}
        if(hidden)item {EmptyState("Unlock to continue","Use the eye control to authenticate before exporting, backing up, or restoring financial records.")}
        item {SectionCard("Export active profile") {Text("CSV and JSON exports are unencrypted. They contain only ${data.workspace.profile.name}'s transactions and metadata; receipt images are included only in encrypted account backups.");OutlinedButton(enabled=!hidden && !busy,onClick={mode="CSV"}){Text("Export CSV")};OutlinedButton(enabled=!hidden && !busy,onClick={mode="JSON"}){Text("Export JSON")}}}
        item {SectionCard("Encrypted account backup") {Text("Back up all profiles, categories, transactions, budgets, receipt images, and supported appearance/privacy preferences. Choose a separate passphrase with at least 12 characters. Account login credentials and biometric keys are never exported.");Button(enabled=!hidden && !busy,onClick={mode="Backup"}){Text("Create encrypted backup")}}}
        item {SectionCard("Restore safely") {Text("Restore into new profiles under your currently authenticated account. Existing profiles are kept. If you lose a local account, first create a new account and restore using your backup passphrase. There is no remote recovery.");OutlinedButton(enabled=!hidden && !busy,onClick={mode="Restore"}){Text("Restore encrypted backup")}}}
        item {Text("Backups are limited to 80 MB before encryption. Save them outside app-private storage. Losing both your local account and backup passphrase makes recovery impossible. Transfers and reminder preferences are not cloud-synced.",style=MaterialTheme.typography.bodySmall)}
    }
    if(mode.isNotEmpty() && !hidden) AlertDialog(onDismissRequest={mode="";password="";confirm=""},title={Text(when(mode){"CSV","JSON"->"Export unencrypted data?";"Backup"->"Set backup passphrase";else->"Restore into new profiles?"})},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(mode in listOf("CSV","JSON"))Text("Anyone with this file can read its financial information. Choose a trusted destination.")
        else {Text(if(mode=="Backup")"Keep this separate passphrase safe. It cannot be recovered." else "Existing records will be kept. Supported app appearance and privacy preferences will be restored from the backup. Enter its passphrase.");FormField("Backup passphrase",password,{password=it},password=true);if(mode=="Backup")FormField("Confirm passphrase",confirm,{confirm=it},password=true)}
    }},confirmButton={TextButton(onClick={when(mode){"CSV"->{authorization.authorize(scope,"CSV",!vm.hidden.value);mode="";exportCsv.launch("BudgetSphere.csv")};"JSON"->{authorization.authorize(scope,"JSON",!vm.hidden.value);mode="";exportJson.launch("BudgetSphere.json")};"Backup"->{if(password.length !in 12..256 || password!=confirm)vm.notify("Use 12–256 characters and matching passphrases")else{authorization.authorize(scope,"Backup",!vm.hidden.value);mode="";backup.launch("BudgetSphere.bsp")}};"Restore"->{if(password.length !in 12..256)vm.notify("Enter the backup passphrase (12–256 characters)")else{authorization.authorize(scope,"Restore",!vm.hidden.value);mode="";restore.launch(arrayOf("application/octet-stream","*/*"))}}}}){Text("Continue")}},dismissButton={TextButton(onClick={mode="";password="";confirm=""}){Text("Cancel")}})
}
