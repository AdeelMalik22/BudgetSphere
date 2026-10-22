package com.budgetsphere.app

import com.budgetsphere.app.ui.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LoadStateTest {
    @Test
    fun readFailuresBecomeRetryStatesWithoutExposingRawExceptions() = runTest {
        val failed =
            flow<Int> { throw java.io.IOException("sensitive internal input") }
                .asLoadState()
                .toList()
        assertEquals(listOf(LoadState.Loading, LoadState.Failed), failed)
        val success = flowOf(123).asLoadState().toList()
        assertEquals(listOf(LoadState.Loading, LoadState.Ready(123)), success)
    }
}
