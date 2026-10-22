package com.budgetsphere.app.domain

/** A single explicit user decision may finish after the external file picker backgrounds us. */
class DocumentAuthorization {
    private var pending: Pair<Scope,String>?=null
    fun authorize(scope: Scope,operation: String,valuesVisible: Boolean) {
        require(valuesVisible) {"Authenticate before opening a financial document operation"}
        pending=scope to operation
    }
    fun consume(scope: Scope?,operation: String): Boolean {
        val grant=pending;pending=null
        return grant!=null && grant.first==scope && grant.second==operation
    }
}
