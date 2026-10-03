package com.fserver.app.presentation.model

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * A piece of user-facing text that is only turned into a `String` where the resources are — so
 * state can name what to show without holding a `Context` and without baking in a locale.
 */
@Immutable
sealed interface UiText {

    /** Already user-facing and not translatable — a device name, a path, a label the user typed. */
    data class Text(val text: String) : UiText

    /** An argument that is itself a [UiText] is resolved first. */
    data class Resource(@param:StringRes val res: Int, val args: List<Any> = emptyList()) : UiText

    /** A resource whose one argument is a byte count, formatted the way the platform does it. */
    data class Size(@param:StringRes val res: Int, val bytes: Long) : UiText

    /** Preferred: recomposes on a configuration change, which a `Context` read once does not. */
    @Composable
    fun asString(): String = when (this) {
        is Text -> text
        is Resource -> stringResource(res, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
        is Size -> stringResource(res, Formatter.formatShortFileSize(LocalContext.current, bytes))
    }

    /** For the callers outside a composition — a snackbar, a notification. */
    fun asString(context: Context): String = when (this) {
        is Text -> text
        is Resource -> context.getString(res, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
        is Size -> context.getString(res, Formatter.formatShortFileSize(context, bytes))
    }

    companion object {
        fun of(@StringRes res: Int, vararg args: Any): UiText = Resource(res, args.toList())
    }
}
