package com.budgetsphere.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetsphere.app.core.*
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import com.budgetsphere.app.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel class AppViewModel @Inject constructor(val accounts: AccountStore,val finance: FinanceStore,val settings: Settings,val privacy: PrivacyManager): ViewModel() {
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val preferences=settings.preferences.stateIn(viewModelScope,SharingStarted.Eagerly,Preferences())
    val profiles=finance.profiles().catch {message.value="Profiles could not be loaded. Check available storage and reopen the app.";emit(emptyList())}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val session=accounts.session
    val workspace=finance.workspace
    val hidden=privacy.hidden
    private var operation: Job?=null
    init {
        viewModelScope.launch {
            accounts.session.map {it?.id}.distinctUntilChanged().collectLatest { account->
                finance.clearWorkspace()
                if(account!=null) {
                    try {
                        finance.restoreLastProfile()
                    }catch(cancel: CancellationException){throw cancel}catch(_:Exception){message.value="Your last workspace could not be restored. Check storage or choose a profile."}
                }
            }
        }
    }
    fun perform(success: String?=null, block: suspend ()->Unit) {
        if(busy.value) return
        operation=viewModelScope.launch {
            busy.value=true
            try { block();if(success!=null) message.value=success }
            catch(cancel: CancellationException) {throw cancel}
            catch(e: Exception) {message.value=friendly(e)}
            finally {busy.value=false}
        }
    }
    fun notify(text: String) {message.value=text}
    fun logout() { operation?.cancel();finance.clearWorkspace();accounts.logout() }
    fun select(id: String)=perform { finance.select(id) }
    fun signup(name: String,identifier: String,password: String,confirm: String)=perform { accounts.signup(name,identifier,password.toCharArray(),confirm.toCharArray()) }
    fun login(identifier: String,password: String)=perform { accounts.login(identifier,password.toCharArray()) }
    fun reveal(password: String, scope: Scope, challenge: Long, done: ()->Unit)=perform {
        val verified=accounts.verify(password.toCharArray())
        require(privacy.revealAfterVerification(challenge,scope,verified)) { "Verification failed or the workspace changed. Values remain hidden." };done()
    }
    private fun friendly(e: Exception): String = when(e) {
        is android.database.sqlite.SQLiteConstraintException -> "This name or budget already exists, or a related record changed. Check your selection."
        is java.io.IOException -> "Storage is unavailable or full. No successful save was confirmed."
        is IllegalArgumentException, is IllegalStateException -> e.message?:"Check your input and try again."
        else -> "The operation could not complete. Your existing data is preserved. Please try again."
    }
}
