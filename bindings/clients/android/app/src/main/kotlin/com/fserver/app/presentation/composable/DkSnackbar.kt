package com.fserver.app.presentation.composable

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fserver.app.presentation.error.AppError
import com.fserver.app.presentation.model.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbarController = compositionLocalOf<DkSnackbarController> {
    error("No SnackbarController provided")
}

@Composable
fun createSnackbarControllerState(
    snackbarHostState: SnackbarHostState,
): ProvidedValue<DkSnackbarController> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val controller = remember(coroutineScope, snackbarHostState) {
        DkSnackbarController(context, coroutineScope, snackbarHostState)
    }

    return LocalSnackbarController provides controller
}

class DkSnackbarController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
) {
    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    fun showSnackbar(@StringRes resId: Int) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(resId))
        }
    }

    fun showSnackbar(text: UiText) {
        showSnackbar(text.asString(context))
    }

    /** The detail follows the message on the same snackbar: two lines, one dismissal. */
    fun showSnackbar(error: AppError) {
        showSnackbar(
            listOfNotNull(error.message, error.detail)
                .joinToString(separator = "\n") { it.asString(context) }
        )
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
