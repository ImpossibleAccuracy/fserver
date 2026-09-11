package com.fserver.app.presentation.screens.source.shared.done

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.done.model.SourceDoneState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceDoneScreen(
    modifier: Modifier = Modifier,
    key: Destination.Source.Done,
    viewModel: SourceDoneViewModel = koinViewModel { parametersOf(key) },
    navigateToFiles: () -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SourceDoneContent(
        modifier = modifier,
        state = state,
        navigateToFiles = navigateToFiles,
        navigateToSourcePick = navigateToSourcePick,
    )
}

@Composable
private fun SourceDoneContent(
    modifier: Modifier = Modifier,
    state: SourceDoneState,
    navigateToFiles: () -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_done_to_files),
                    onClick = navigateToFiles,
                )
                if (state.canAddAnother) {
                    DkGhostButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.source_done_add_another),
                        onClick = navigateToSourcePick,
                    )
                }
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
                text = stringResource(state.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.source_done_body, state.peerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Column(modifier = Modifier.padding(top = DkSpacing.sm)) {
                state.summary.forEachIndexed { index, row ->
                    DkValueRow(
                        title = stringResource(row.labelRes),
                        value = row.value.text(),
                    )
                    if (index != state.summary.lastIndex) {
                        DkFadingDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceDoneState.Value.text(): String = when (this) {
    is SourceDoneState.Value.Text -> text
    is SourceDoneState.Value.Resource -> stringResource(res, *args.toTypedArray())
    is SourceDoneState.Value.Size -> stringResource(res, FileSize(bytes).formatted())
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceDoneAutoUploadPreview() {
    FServerTheme {
        SourceDoneContent(
            state = SourceDoneState(
                mode = SourceModeUi.AutoUpload,
                peerName = "HOME-NAS",
                summary = listOf(
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_source,
                        SourceDoneState.Value.Text("Photos and videos"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_target,
                        SourceDoneState.Value.Text("HOME-NAS"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_mode,
                        SourceDoneState.Value.Resource(R.string.mode_autoupload_title),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_sent_from,
                        SourceDoneState.Value.Resource(R.string.source_summary_location_media),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_conditions,
                        SourceDoneState.Value.Resource(R.string.source_summary_scope_new),
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}

@Preview(name = "Offload", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceDoneOffloadPreview() {
    FServerTheme {
        SourceDoneContent(
            state = SourceDoneState(
                mode = SourceModeUi.Offload,
                peerName = "HOME-NAS",
                summary = listOf(
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_source,
                        SourceDoneState.Value.Text("DCIM/Projects"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_target,
                        SourceDoneState.Value.Text("HOME-NAS"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_mode,
                        SourceDoneState.Value.Resource(R.string.mode_offload_title),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_sent_from,
                        SourceDoneState.Value.Text("/storage/emulated/0/DCIM/Projects"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_rule,
                        SourceDoneState.Value.Resource(
                            R.string.source_summary_rule_older,
                            listOf(60),
                        ),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_pinned,
                        SourceDoneState.Value.Resource(R.string.source_summary_pinned_kept),
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}

@Preview(name = "Accepted", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceDoneIncomingPreview() {
    FServerTheme {
        SourceDoneContent(
            state = SourceDoneState(
                role = SourceRoleUi.Follower,
                mode = SourceModeUi.Sync,
                peerName = "MacBook-Pro",
                summary = listOf(
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_source,
                        SourceDoneState.Value.Text("DCIM/Projects"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_peer,
                        SourceDoneState.Value.Text("MacBook-Pro"),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_mode,
                        SourceDoneState.Value.Resource(R.string.mode_sync_title),
                    ),
                    SourceDoneState.SummaryRow(
                        R.string.source_summary_saved_to,
                        SourceDoneState.Value.Resource(
                            R.string.sync_request_location_internal_title,
                        ),
                    ),
                ),
            ),
            navigateToFiles = {},
            navigateToSourcePick = {},
        )
    }
}
