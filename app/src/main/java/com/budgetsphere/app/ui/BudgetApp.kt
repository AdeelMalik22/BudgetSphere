package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.budgetsphere.app.core.Biometrics
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import java.security.MessageDigest

@Composable
fun BudgetApp(
    vm: AppViewModel,
    biometrics: Biometrics,
    receipts: ReceiptStore,
    portability: Portability,
) {
    val account by vm.session.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activeWorkspace by vm.workspace.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            vm.message.value = null
            snackbar.showSnackbar(it)
        }
    }
    BudgetSphereTheme(
        prefs.theme,
        activeWorkspace?.profile?.color?.let { androidx.compose.ui.graphics.Color(it) },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                key(account?.id) {
                    if (account == null) AuthScreen(vm, biometrics)
                    else AccountWorkspace(vm, biometrics, receipts, portability)
                }
            }
        }
    }
}

@Composable
private fun AccountWorkspace(
    vm: AppViewModel,
    biometrics: Biometrics,
    receipts: ReceiptStore,
    portability: Portability,
) {
    val workspace by vm.workspace.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf(false) }
    if (workspace == null || manage) {
        Column(Modifier.safeDrawingPadding()) {
            Row {
                if (workspace != null)
                    TextButton(onClick = { manage = false }) { Text("Back to workspace") }
                TextButton(onClick = vm::logout) { Text("Log out") }
            }
            Box(Modifier.weight(1f)) { ProfilesScreen(vm, { manage = false }) }
        }
        return
    }
    val selected = workspace ?: return
    key(selected.scope, selected.profile) {
        var retry by remember { mutableIntStateOf(0) }
        val flow = remember(selected, retry) { vm.finance.observeWorkspace(selected).asLoadState() }
        val load by key(flow) { flow.collectAsStateWithLifecycle(initialValue = LoadState.Loading) }
        when (val state = load) {
            LoadState.Loading ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            is LoadState.Ready ->
                WorkspaceNavigation(
                    state.data,
                    vm,
                    biometrics,
                    receipts,
                    portability,
                    { manage = true },
                )
            LoadState.Failed ->
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EmptyState(
                        "Workspace unavailable",
                        "We couldn't read this workspace. Check available storage, then retry.",
                    )
                    Button(onClick = { retry++ }) { Text("Retry") }
                    TextButton(onClick = { manage = true }) { Text("Manage profiles") }
                    TextButton(onClick = vm::logout) { Text("Log out") }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceNavigation(
    data: WorkspaceData,
    vm: AppViewModel,
    biometrics: Biometrics,
    receipts: ReceiptStore,
    portability: Portability,
    manageProfiles: () -> Unit,
) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val account by vm.session.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var switcher by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var challenge by remember { mutableLongStateOf(0) }
    var enrollment by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf("") }
    val tabs =
        listOf(
            "Home" to Icons.Outlined.Home,
            "Transactions" to Icons.AutoMirrored.Outlined.ReceiptLong,
            "Analytics" to Icons.Outlined.BarChart,
            "Budgets" to Icons.Outlined.AccountBalanceWallet,
            "Settings" to Icons.Outlined.Settings,
        )
    fun navigate(destination: String) {
        nav.navigate(destination) {
            popUpTo("Home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Hello, ${account?.name?.substringBefore(' ')?:"there"}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(data.workspace.profile.name)
                    }
                },
                navigationIcon = {
                    if (route !in tabs.map { it.first })
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                },
                actions = {
                    Box {
                        IconButton(onClick = { switcher = true }) {
                            Icon(Icons.Outlined.SwitchAccount, "Switch profile")
                        }
                        DropdownMenu(switcher, { switcher = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        switcher = false
                                        vm.select(profile.id)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Manage profiles") },
                                onClick = {
                                    switcher = false
                                    manageProfiles()
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (hidden) {
                                challenge = vm.privacy.challenge()
                                reveal = true
                            } else vm.privacy.hide()
                        }
                    ) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (hidden) "Show financial information — authentication required"
                            else "Hide all financial information",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (name, icon) ->
                    NavigationBarItem(
                        selected = route == name,
                        onClick = { navigate(name) },
                        icon = { Icon(icon, name) },
                        label = { Text(name, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "Home", modifier = Modifier.padding(padding)) {
            composable("Home") {
                Dashboard(
                    data,
                    vm,
                    hidden,
                    { kind -> nav.navigate("add/$kind") },
                    { id -> nav.navigate("transaction/$id") },
                    ::navigate,
                )
            }
            composable("Transactions") {
                TransactionsScreen(
                    data,
                    vm,
                    hidden,
                    { nav.navigate("transaction/$it") },
                    { nav.navigate("add/EXPENSE") },
                )
            }
            composable("Analytics") { AnalyticsScreen(data, vm, hidden) }
            composable("Budgets") { BudgetsScreen(data, vm, hidden) }
            composable("Settings") {
                SettingsScreen(
                    data,
                    vm,
                    biometrics,
                    { if (it == "Profiles") manageProfiles() else nav.navigate(it) },
                    { enrollment = true },
                    { deletion = "account" },
                    { deletion = "profile" },
                )
            }
            composable("Categories") { CategoriesScreen(data, vm) }
            composable("Portability") { PortabilityScreen(data, vm, portability, hidden) }
            composable("add/{kind}") { back ->
                TransactionForm(data, vm, null, back.arguments?.getString("kind") ?: "EXPENSE") { id
                    ->
                    nav.popBackStack()
                    nav.navigate("transaction/$id")
                }
            }
            composable("edit/{id}") { back ->
                val tx = data.transactions.find { it.id == back.arguments?.getString("id") }
                if (tx != null) TransactionForm(data, vm, tx, tx.kind) { nav.popBackStack() }
                else EmptyState("Transaction unavailable", "It may have been deleted.")
            }
            composable("transaction/{id}") { back ->
                val tx = data.transactions.find { it.id == back.arguments?.getString("id") }
                if (tx != null)
                    TransactionDetails(
                        data,
                        tx,
                        vm,
                        hidden,
                        { nav.navigate("edit/${tx.id}") },
                        { nav.popBackStack() },
                    ) {
                        Receipts(data, tx, receipts, vm, hidden)
                    }
                else EmptyState("Transaction unavailable", "It may have been deleted.")
            }
        }
    }
    if (reveal)
        PasswordGate(
            "Reveal financial information",
            vm,
            { reveal = false },
            { password -> vm.reveal(password, data.workspace.scope, challenge) { reveal = false } },
            biometricAction =
                if (account?.biometricCipher != null && biometrics.availability() == null) {
                    {
                        val captured = account!!
                        biometrics.unlock(
                            captured,
                            { plain ->
                                val valid =
                                    MessageDigest.isEqual(
                                        plain,
                                        captured.id.toByteArray(Charsets.UTF_8),
                                    )
                                plain.fill(0)
                                if (
                                    vm.privacy.revealAfterVerification(
                                        challenge,
                                        data.workspace.scope,
                                        valid,
                                    )
                                )
                                    reveal = false
                                else vm.notify("Verification expired. Values remain hidden.")
                            },
                            { vm.notify(it) },
                        )
                    }
                } else null,
        )
    if (enrollment)
        PasswordGate(
            "Confirm password to enable biometrics",
            vm,
            { enrollment = false },
            { password ->
                vm.perform {
                    require(vm.accounts.verify(password.toCharArray())) { "Incorrect password" }
                    val captured = vm.accounts.requireAccount()
                    biometrics.enroll(
                        captured,
                        { cipher, iv ->
                            vm.perform("Biometric unlock enabled") {
                                vm.accounts.enableBiometric(captured.id, cipher, iv)
                                enrollment = false
                            }
                        },
                        { vm.notify(it) },
                    )
                }
            },
        )
    if (deletion.isNotEmpty())
        PasswordGate(
            if (deletion == "account") "Permanently delete this account?"
            else "Permanently delete this profile?",
            vm,
            { deletion = "" },
            { password ->
                val target = deletion
                vm.perform("Data deleted") {
                    require(vm.accounts.verify(password.toCharArray())) { "Incorrect password" }
                    if (target == "profile") vm.finance.deleteProfile(data.workspace.scope.profile)
                    else vm.finance.deleteCurrentAccount()
                    deletion = ""
                }
            },
            warning =
                "This cannot be undone. All associated transactions, categories, budgets, and receipts will be deleted. Enter your account password to confirm.",
        )
}

@Composable
private fun PasswordGate(
    title: String,
    vm: AppViewModel,
    dismiss: () -> Unit,
    submit: (String) -> Unit,
    biometricAction: (() -> Unit)? = null,
    warning: String? = null,
) {
    var password by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                warning?.let { Text(it) }
                FormField("Account password", password, { password = it }, password = true)
                if (biometricAction != null)
                    OutlinedButton(enabled = !busy, onClick = biometricAction) {
                        Text("Use biometrics")
                    }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val value = password
                    password = ""
                    submit(value)
                },
            ) {
                Text("Verify password")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
