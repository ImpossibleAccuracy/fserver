package com.fserver.app.presentation.screens.source.details.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.theme.FServerTheme

/** The source's recent passes, one row a day, ending in the way out to the full feed. */
@Composable
fun SourceHistory(
    modifier: Modifier = Modifier,
    history: List<SourceDetailsState.HistoryUi>,
    onEntryClick: () -> Unit,
    onFullHistoryClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        history.forEach { entry ->
            DkListRow(
                title = entry.dateLabel,
                subtitle = entry.detail,
                subtitleColor = MaterialTheme.colorScheme.error.takeIf { entry.warning },
                onClick = onEntryClick,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                    ) {
                        DkCaption(text = entry.timeLabel)
                        DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                    }
                },
            )
            DkFadingDivider()
        }
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onFullHistoryClick)
                .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
            text = stringResource(R.string.source_details_full_history),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SourceHistoryPreview() {
    FServerTheme {
        SourceHistory(
            history = SourceDetailsState.SampleAutoUpload.history,
            onEntryClick = {},
            onFullHistoryClick = {},
        )
    }
}
