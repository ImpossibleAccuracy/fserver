package com.fserver.app.presentation.shared.error

import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.app.presentation.model.UiText
import com.fserver.core.requirement.RequirementReport

/**
 * A failure as the UI shows it: what to say, and — where there is one — the fix.
 *
 * Text rather than a `String` on purpose: the parser runs in a view model, and the locale is only
 * known where the text is drawn.
 */
@Immutable
data class AppError(
    val message: UiText,
    /** The line under [message], when there is something more specific worth reading. */
    val detail: UiText? = null,
    /**
     * Set when the failure is a permission, a radio or a network the user can act on. The
     * requirements sheet renders it; everything else is a snackbar.
     */
    val requirements: RequirementReport? = null,
) {
    /** Whether the user has something to do about it beyond reading the line. */
    val isActionable: Boolean get() = requirements?.isSatisfied == false

    companion object {
        /** A folder picked from this app's own documents provider. */
        val OwnFolder = AppError(UiText.of(R.string.error_own_folder))
    }
}
