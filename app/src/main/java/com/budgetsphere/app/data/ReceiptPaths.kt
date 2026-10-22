package com.budgetsphere.app.data

import android.content.Context
import com.budgetsphere.app.domain.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class ReceiptPaths @Inject constructor(@param:ApplicationContext private val context: Context) {
    private fun valid(id: String) {require(runCatching {java.util.UUID.fromString(id)}.isSuccess){"Invalid ownership identifier"}}
    fun accountDirectory(account: String): File {valid(account);return File(context.filesDir,"receipts/$account")}
    fun directory(scope: Scope): File {valid(scope.profile);return File(accountDirectory(scope.account),scope.profile)}
    fun file(scope: Scope,name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}(-thumb)?\\.jpg"))) {"Invalid receipt reference"}
        return File(directory(scope),name)
    }
}
