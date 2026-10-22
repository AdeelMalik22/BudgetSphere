package com.budgetsphere.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.budgetsphere.app.core.Biometrics
import com.budgetsphere.app.data.Portability
import com.budgetsphere.app.data.ReceiptStore
import com.budgetsphere.app.ui.AppViewModel
import com.budgetsphere.app.ui.BudgetApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    @Inject lateinit var receipts: ReceiptStore
    @Inject lateinit var portability: Portability
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen();super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            combine(vm.hidden,vm.preferences){hidden,prefs->hidden || prefs.screenshotProtection}.collect {secure->
                if(secure)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent {BudgetApp(vm,Biometrics(this),receipts,portability)}
    }
    override fun onStop() {
        // Recents must never capture financial content, even when user screenshots are enabled.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if(vm.preferences.value.autoHide)vm.privacy.hide()
        super.onStop()
    }
    override fun onStart() {super.onStart();if(vm.hidden.value || vm.preferences.value.screenshotProtection)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
}
