package com.budgetsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.core.Biometrics
import com.budgetsphere.app.domain.Validation

@Composable
fun AuthScreen(vm: AppViewModel, biometrics: Biometrics) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    var submitted by remember { mutableStateOf(false) }
    val nameError = if (signup) validationError { Validation.name(name) } else null
    val identifierError = validationError { Validation.identifier(identifier) }
    val passwordError = validationError { if (signup) Validation.password(password) else require(password.isNotEmpty()) { "Enter your password" } }
    val confirmError = if (signup) validationError { require(password == confirm) { "Passwords do not match" } } else null
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f), MaterialTheme.colorScheme.background)))) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandMark()
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (signup) "A calmer way to manage money" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
                Text(if (signup) "Create a private workspace for the life you are building." else "Your financial world is ready when you are.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthMode("Log in", !signup) { signup = false; submitted = false }
                        AuthMode("Sign up", signup) { signup = true; submitted = false }
                    }
                    if (signup) FormField("Full name", name, { name = it }, error = if (submitted) nameError else null)
                    FormField("Username or email", identifier, { identifier = it }, error = if (submitted) identifierError else null)
                    FormField("Password", password, { password = it }, password = true, error = if (submitted) passwordError else null)
                    if (signup) {
                        FormField("Confirm password", confirm, { confirm = it }, password = true, error = if (submitted) confirmError else null)
                        Text("Use at least 12 characters. This account stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            submitted = true
                            if (listOf(nameError, identifierError, passwordError, confirmError).all { it == null }) {
                                if (signup) vm.signup(name, identifier, password, confirm) else vm.login(identifier, password)
                                password = ""; confirm = ""; submitted = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 15.dp),
                    ) { Text(if (busy) "Please wait…" else if (signup) "Create account" else "Log in") }
                    if (!signup) {
                        Row(verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f)); Text("  or  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f)) }
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = {
                                vm.perform {
                                    val account = vm.accounts.biometricAccount(identifier) ?: error("Enter your username/email and enable biometrics after a password login.")
                                    biometrics.unlock(account, { decrypted -> vm.perform { vm.accounts.biometricLogin(account, decrypted) } }, { vm.notify(it) })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Outlined.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Unlock with biometrics") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Private by design · no cloud account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { signup = !signup; password = ""; confirm = ""; submitted = false }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (signup) "Already have a local account? Log in" else "New here? Create local account")
            }
            Text("Keep an encrypted backup and its separate passphrase safe. Uninstalling or clearing app data removes local records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.AuthMode(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Box(Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
