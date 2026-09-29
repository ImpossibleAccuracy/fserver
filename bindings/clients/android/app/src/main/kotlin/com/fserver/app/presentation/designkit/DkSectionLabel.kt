package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Accent-coloured, letter-spaced section heading — settings groups, deck sub-headers.
 * [trailing] fills the right edge of the same line, which is where the deck puts a section's
 * running count ("2 selected", "2 of 3 running", "Found 2").
 */
@Composable
fun DkSectionLabel(
    modifier: Modifier = Modifier,
    text: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        // Only the counter variant needs the full width; without it the label stays
        // wrap-content, as every existing caller expects.
        modifier = modifier
            .then(if (trailing != null) Modifier.fillMaxWidth() else Modifier)
            .padding(top = DkSpacing.md, bottom = DkSpacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall
                .copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** A section heading with its item count on the right. */
@Composable
fun DkSectionLabel(
    modifier: Modifier = Modifier,
    text: String,
    count: Int,
) = DkSectionLabel(modifier = modifier, text = text, trailing = { DkCaption(text = count.toString()) })

/** Muted caption used under a title or above a list — the deck's `--color-neutral-600` voice. */
@Composable
fun DkCaption(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

/** Monospaced technical caption: paths, addresses, breadcrumbs. */
@Composable
fun DkMonoCaption(modifier: Modifier = Modifier, text: String) {
    Text(
        modifier = modifier,
        text = text,
        style = DkType.mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
