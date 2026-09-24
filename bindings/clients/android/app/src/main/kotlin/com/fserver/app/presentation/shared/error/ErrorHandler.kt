package com.fserver.app.presentation.shared.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.composable.RequirementsSheet
import com.fserver.app.presentation.permission.rememberRequirementResolver
import com.fserver.core.requirement.RequirementReport
import kotlinx.coroutines.flow.Flow

/**
 * Shows what the app failed at, the one way it shows it.
 *
 * A failure the user can clear opens the requirements sheet, because a snackbar has nowhere to put
 * a permission button and disappears before it could be read. Everything else is a line in the
 * global snackbar.
 *
 * Placed once, at the root, over [ErrorBus]: a failure is not tied to the screen that caused it,
 * and a handler per screen would lose the ones raised while no screen is listening.
 *
 * [onResolved] fires after the user is done with the sheet, granted or not. Whoever asked has to
 * try again to find out what the user actually did, and only that caller knows what it was trying
 * to do, so nothing here retries on its own.
 */
@Composable
fun ErrorHandler(
    errors: Flow<AppError>,
    onResolved: () -> Unit = {},
) {
    val snackbar = LocalSnackbarController.current
    var requirements by remember { mutableStateOf<RequirementReport?>(null) }

    val resolver = rememberRequirementResolver {
        requirements = null
        onResolved()
    }

    LaunchedEffect(errors, snackbar) {
        errors.collect { error ->
            val report = error.requirements
            if (report != null && !report.isSatisfied) {
                requirements = report
            } else {
                snackbar.showSnackbar(error)
            }
        }
    }

    requirements?.let { report ->
        RequirementsSheet(
            report = report,
            resolver = resolver,
            onDismiss = { requirements = null },
        )
    }
}
