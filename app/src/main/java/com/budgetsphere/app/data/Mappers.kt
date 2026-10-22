package com.budgetsphere.app.data

import com.budgetsphere.app.domain.*

fun AccountEntity.toDomain() =
    LocalAccount(id, name, identifier, biometricCipher?.copyOf(), biometricIv?.copyOf())

fun ProfileEntity.toDomain() = Profile(accountId, id, name, description, currency, color, icon)

fun CategoryEntity.toDomain() = Category(accountId, profileId, id, name, kind, color, icon, custom)

fun TransactionEntity.toDomain() =
    FinancialTransaction(
        accountId,
        profileId,
        id,
        title,
        amount,
        currency,
        kind,
        categoryId,
        date,
        description,
        notes,
        payment,
        tags,
        createdAt,
        updatedAt,
    )

fun BudgetEntity.toDomain() =
    Budget(accountId, profileId, id, categoryId, categoryKey, month, amount)

fun AttachmentEntity.toDomain() =
    Attachment(accountId, profileId, id, transactionId, filename, thumbnail)
