package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.core.Biometrics
import com.budgetsphere.app.domain.Validation

@Composable fun AuthScreen(vm: AppViewModel,biometrics: Biometrics) {
    var signup by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")};var identifier by remember {mutableStateOf("")};var password by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    val busy by vm.busy.collectAsState()
    var submitted by remember {mutableStateOf(false)}
    val nameError=if(signup)validationError{Validation.name(name)}else null
    val identifierError=validationError{Validation.identifier(identifier)}
    val passwordError=validationError{if(signup)Validation.password(password)else require(password.isNotEmpty()){"Enter your password"}}
    val confirmError=if(signup)validationError{require(password==confirm){"Passwords do not match"}}else null
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp));Icon(Icons.Outlined.AccountBalanceWallet,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
        Text("BudgetSphere",style=MaterialTheme.typography.headlineLarge)
        Text("Your finances. Your device.",style=MaterialTheme.typography.titleMedium)
        Text(if(signup) "Create a private account" else "Welcome back",style=MaterialTheme.typography.headlineSmall)
        if(signup) FormField("Full name",name,{name=it},error=if(submitted)nameError else null)
        FormField("Username or email",identifier,{identifier=it},error=if(submitted)identifierError else null)
        FormField("Password",password,{password=it},password=true,error=if(submitted)passwordError else null)
        if(signup) {FormField("Confirm password",confirm,{confirm=it},password=true,error=if(submitted)confirmError else null);Text("Use at least 12 characters. Accounts are local to this installation. There is no email verification or remote password reset.",style=MaterialTheme.typography.bodySmall)}
        Button(enabled=!busy,onClick={submitted=true;if(listOf(nameError,identifierError,passwordError,confirmError).all {it==null}){if(signup) vm.signup(name,identifier,password,confirm) else vm.login(identifier,password);password="";confirm="";submitted=false}},modifier=Modifier.fillMaxWidth()) {Text(if(busy) "Please wait…" else if(signup) "Create account" else "Log in")}
        if(!signup) OutlinedButton(enabled=!busy,onClick={
            vm.perform {
                val account=vm.accounts.biometricAccount(identifier)?:error("Enter your username/email and enable biometrics after a password login.")
                biometrics.unlock(account,{decrypted->vm.perform {vm.accounts.biometricLogin(account,decrypted)}},{vm.notify(it)})
            }
        },modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Fingerprint,null);Spacer(Modifier.width(8.dp));Text("Biometric unlock")}
        TextButton(onClick={signup=!signup;password="";confirm="";submitted=false},enabled=!busy) {Text(if(signup) "Already have a local account? Log in" else "New here? Create local account")}
        Text("Everything stays offline. Keep an encrypted backup and its passphrase somewhere safe. Uninstalling or clearing app data removes local accounts and records.",style=MaterialTheme.typography.bodySmall)
    }
}
