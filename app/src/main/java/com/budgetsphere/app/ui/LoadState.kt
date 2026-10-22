package com.budgetsphere.app.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Ready<T>(val data: T) : LoadState<T>

    data object Failed : LoadState<Nothing>
}

fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> =
    map<T, LoadState<T>> { LoadState.Ready(it) }
        .onStart { emit(LoadState.Loading) }
        .catch { emit(LoadState.Failed) }
