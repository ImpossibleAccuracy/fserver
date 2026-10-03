package com.fserver.app.presentation.shared.journal.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.dateLabel
import com.fserver.app.presentation.composable.model.timeLabel
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.icon
import com.fserver.app.presentation.theme.FServerTheme

/**
 * One journal entry: what happened, its details and when. An issue shows how often it came back
 * and, while open, a way to dismiss it when [onDismiss] is set.
 */
@Composable
fun JournalEntryRow(
    modifier: Modifier = Modifier,
    entry: JournalEntryUi,
    showDate: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    contentPaddings: PaddingValues = PaddingValues(
        horizontal = DkSpacing.screenPadding,
        vertical = DkSpacing.md,
    ),
) {
    val time = if (showDate) "${entry.at.dateLabel()} ${entry.at.timeLabel()}" else entry.at.timeLabel()
    val details = entry.details.map { it.asString() } + time

    DkListRow(
        modifier = modifier,
        title = entry.title.asString(),
        titleMaxLines = 2,
        subtitle = details.joinToString(" · "),
        subtitleMaxLines = 3,
        subtitleColor = MaterialTheme.colorScheme.error.takeIf { entry.isOpenIssue },
        onClick = onClick,
        leading = { DkThumbnail(icon = entry.kind.icon) },
        trailing = entry.issue?.let { issue ->
            {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                ) {
                    if (issue.occurrences > 1) {
                        DkTag(
                            text = stringResource(R.string.journal_issue_repeats, issue.occurrences),
                            style = DkTagStyle.Outline,
                        )
                    }
                    when {
                        issue.open && onDismiss != null -> DkGhostButton(
                            text = stringResource(R.string.action_dismiss),
                            onClick = onDismiss,
                        )

                        issue.open -> DkTag(text = stringResource(R.string.journal_issue_open), style = DkTagStyle.Accent)
                        else -> DkTag(text = stringResource(R.string.journal_issue_solved), style = DkTagStyle.Neutral)
                    }
                }
            }
        },
        contentPaddings = contentPaddings,
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun JournalEntryRowPreview() {
    FServerTheme {
        Column {
            JournalEntryUi.Samples.forEach { entry ->
                JournalEntryRow(entry = entry, onDismiss = {})
            }
        }
    }
}
