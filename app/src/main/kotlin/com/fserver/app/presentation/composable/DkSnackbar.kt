package com.fserver.app.presentation.composable

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbarController = compositionLocalOf<DkSnackbarController> {
    error("No SnackbarController provided")
}

@Composable
fun createSnackbarControllerState(
    snackbarHostState: SnackbarHostState,
): ProvidedValue<DkSnackbarController> {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(coroutineScope, snackbarHostState) {
        DkSnackbarController(coroutineScope, snackbarHostState)
    }

    return LocalSnackbarController provides controller
}

class DkSnackbarController(
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
) {
    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
}

@Composable
fun DkSnackbar(
    modifier: Modifier = Modifier,
    state: SnackbarHostState,
) {
    // TODO: add swipe to dismiss
    SnackbarHost(
        modifier = modifier,
        hostState = state,
    )
}
