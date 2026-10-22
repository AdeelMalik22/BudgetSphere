package com.budgetsphere.app.core

import com.budgetsphere.app.domain.Scope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A reveal grant belongs to one session/scope and cannot be reused after a switch. */
@Singleton class PrivacyManager @Inject constructor() {
    private val _hidden=MutableStateFlow(true)
    val hidden=_hidden.asStateFlow()
    private var scope: Scope?=null
    private var generation=0L
    @Synchronized fun bind(next: Scope?) { scope=next; generation++; _hidden.value=true }
    @Synchronized fun hide() { generation++; _hidden.value=true }
    @Synchronized fun challenge(): Long = generation
    @Synchronized fun revealAfterVerification(challenge: Long, expected: Scope, verified: Boolean): Boolean {
        if(!verified || challenge!=generation || scope!=expected) return false
        _hidden.value=false
        return true
    }
}
