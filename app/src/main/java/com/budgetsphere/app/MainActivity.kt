package com.budgetsphere.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.ui.BudgetSphereTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { BudgetSphereTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement=Arrangement.Center) {
                Text("BudgetSphere", style=MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                Text("Your finances. Your device.", style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Private, offline personal finance. Accounts and budgets are stored only on this device.")
            }
        } } }
    }
}
