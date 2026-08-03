package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/** Accent-coloured, letter-spaced section heading — settings groups, deck sub-headers. */
@Composable
fun DkSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = DkSpacing.md, bottom = DkSpacing.xs),
    )
}

/** Muted caption used under a title or above a list — the deck's `--color-neutral-600` voice. */
@Composable
fun DkCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Monospaced technical caption: paths, addresses, breadcrumbs. */
@Composable
fun DkMonoCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = DkType.mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
