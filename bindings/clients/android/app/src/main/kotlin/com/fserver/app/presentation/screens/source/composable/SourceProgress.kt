package com.fserver.app.presentation.screens.source.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkSpacing

/**
 * The seconds between an answer and its consequence: scanning a folder, checking what the
 * target already has, counting what a rule would evict.
 *
 * Short-lived, but not skippable as a screen — there is real work behind it, and the running
 * count is the only preview the user gets before an irreversible mode turns on. Cancel is
 * always offered for the same reason; it lives in the screen's action bar with every other
 * control.
 */
@Composable
fun SourceProgressStep(
    title: String,
    body: String,
    progress: Float,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkInlineSpinner()
        Text(
            modifier = Modifier.padding(top = DkSpacing.lg),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.sm),
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        DkProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DkSpacing.xl),
        )
        DkMonoCaption(
            modifier = Modifier.padding(top = DkSpacing.sm),
            text = detail,
        )
    }
}
