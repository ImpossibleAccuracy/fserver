package com.fserver.app.presentation.screens.source.done

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.screens.source.done.model.SourceDoneState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourceDoneScreen(
    handler: SourceDoneHandler,
    navigateToFiles: () -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    val state = handler.state.collectAsStateWithLifecycle().value ?: return

    SourceDoneScreenContent(
        state = state,
        navigateToFiles = navigateToFiles,
        navigateToSourcePick = navigateToSourcePick,
    )
}

@Composable
private fun SourceDoneScreenContent(
    state: SourceDoneState,
    navigateToFiles: () -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_done_to_files),
                    onClick = navigateToFiles,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_done_add_another),
                    onClick = navigateToSourcePick,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(top = DkSpacing.xxl, bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(state.mode.doneTitleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(state.mode.doneBodyRes, state.targetName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            state.freedLabel?.let { freed ->
                DkCard {
                    Text(
                        text = freed,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DkCaption(
                        text = stringResource(
                            R.string.source_done_offload_freed,
                            state.freedDetail,
                        )
                    )
                }
            }

            Column(modifier = Modifier.padding(top = DkSpacing.sm)) {
                state.summary.forEachIndexed { index, row ->
                    DkValueRow(title = stringResource(row.labelRes), value = row.value)
                    if (index != state.summary.lastIndex) {
                        DkFadingDivider()
                    }
                }
            }
        }
    }
}

private val SourceModeUi.doneTitleRes: Int
    get() = when (this) {
        SourceModeUi.AutoUpload -> R.string.source_done_autoupload_title
        SourceModeUi.Offload -> R.string.source_done_offload_title
        SourceModeUi.Sync -> R.string.source_done_sync_title
        SourceModeUi.Host -> R.string.source_done_host_title
    }

private val SourceModeUi.doneBodyRes: Int
    get() = when (this) {
        SourceModeUi.AutoUpload -> R.string.source_done_autoupload_body
        SourceModeUi.Offload -> R.string.source_done_offload_body
        SourceModeUi.Sync -> R.string.source_done_sync_body
        SourceModeUi.Host -> R.string.source_done_host_body
    }

@Preview(showBackground = true)
@Composable
private fun SourceDoneAutoUploadPreview() {
    FServerTheme {
        SourceDoneScreenContent(
            state = SourceDoneState(
                kind = SourceKindUi.Media,
                mode = SourceModeUi.AutoUpload,
                targetName = "HOME-NAS",
                summary = listOf(
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_source,
                        "Photos and videos",
                    ),
                    SourceDoneState.SummaryRow(R.string.source_summary_target, "HOME-NAS"),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_conditions,
                        "New · Wi-Fi only",
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_queued,
                        "12 files · 240 MB",
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}

@Preview(name = "Offload", showBackground = true)
@Composable
private fun SourceDoneOffloadPreview() {
    FServerTheme {
        SourceDoneScreenContent(
            state = SourceDoneState(
                kind = SourceKindUi.Media,
                mode = SourceModeUi.Offload,
                targetName = "HOME-NAS",
                freedLabel = "~18.4 GB",
                freedDetail = "2,140 files",
                summary = listOf(
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_rule,
                        "Older than 60 days",
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_pinned,
                        "never deleted",
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}

@Preview(name = "Host", showBackground = true)
@Composable
private fun SourceDoneHostPreview() {
    FServerTheme {
        SourceDoneScreenContent(
            state = SourceDoneState(
                kind = SourceKindUi.Folder,
                mode = SourceModeUi.Host,
                targetName = "HOME-NAS",
                summary = listOf(
                    SourceDoneState.SummaryRow(R.string.source_summary_folder, "DCIM/Projects"),
                    SourceDoneState.SummaryRow(R.string.source_summary_rights, "Read only"),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_address,
                        "192.168.1.37:8384",
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}
