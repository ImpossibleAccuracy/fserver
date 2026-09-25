package com.fserver.app.presentation.screens.source.details

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkInfoTone
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.details.composable.AttentionCard
import com.fserver.app.presentation.screens.source.details.composable.FileTrail
import com.fserver.app.presentation.screens.source.details.composable.SourceHeader
import com.fserver.app.presentation.screens.source.details.composable.SourceHistory
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsIntent
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceDetailsScreen(
    modifier: Modifier = Modifier,
    key: Destination.Files.SourceDetails,
    viewModel: SourceDetailsViewModel = koinViewModel { parametersOf(key) },
    navigateToActivity: () -> Unit,
    navigateToFiles: (deviceId: String) -> Unit,
    navigateToDevice: (deviceId: String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SourceDetailsScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToActivity = navigateToActivity,
        navigateToFiles = navigateToFiles,
        navigateToDevice = navigateToDevice,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceDetailsScreenContent(
    modifier: Modifier = Modifier,
    state: SourceDetailsState,
    onIntent: (SourceDetailsIntent) -> Unit,
    navigateToActivity: () -> Unit,
    navigateToFiles: (deviceId: String) -> Unit,
    navigateToDevice: (deviceId: String) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.label,
                onBack = navigateUp,
                actions = { SourceMenu(onEdit = {}, onDelete = {}) },
            )
        },
    ) { innerPadding ->
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = false,
            onRefresh = { onIntent(SourceDetailsIntent.RefreshRequested) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.isLoading) return@Column

                SourceHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigateToFiles(state.peer.id) }
                        .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
                    state = state,
                )

                StatusNote(modifier = gutter.padding(top = DkSpacing.lg), state = state)

                DkSectionLabel(
                    modifier = gutter.padding(top = DkSpacing.md),
                    text = stringResource(R.string.source_details_section_trail),
                )
                FileTrail(modifier = gutter, state = state)

                PeerLine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigateToDevice(state.peer.id) }
                        .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
                    state = state,
                )

                state.sendNow?.let { sendNow ->
                    SendNowOffer(
                        modifier = gutter.padding(top = DkSpacing.lg),
                        count = sendNow.count,
                        bytes = sendNow.bytes,
                        onSend = { onIntent(SourceDetailsIntent.SendNowClicked) },
                    )
                }

                if (state.attention.isNotEmpty()) {
                    DkSectionLabel(
                        modifier = gutter.padding(top = DkSpacing.md),
                        text = stringResource(R.string.source_details_section_attention),
                    )
                    Column(
                        modifier = gutter,
                        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
                    ) {
                        state.attention.forEach { attention ->
                            AttentionCard(
                                attention = attention,
                                peerName = state.peer.name,
                                onResolveConflicts = navigateToActivity,
                            )
                        }
                    }
                }

                if (state.history.isNotEmpty()) {
                    DkSectionLabel(
                        modifier = gutter.padding(top = DkSpacing.md),
                        text = stringResource(R.string.source_details_section_history),
                    )
                    SourceHistory(
                        history = state.history,
                        onEntryClick = navigateToActivity,
                        onFullHistoryClick = navigateToActivity,
                    )
                }

                Spacer(modifier = Modifier.height(DkSpacing.xl))
            }

            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.align(Alignment.TopCenter),
                visible = state.isSyncing,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                DkProgressBar(progress = null)
            }
        }
    }
}

@Composable
private fun SourceMenu(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        DkIconButton(onClick = { expanded = true }, icon = Icons.Default.MoreVert)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_edit)) },
                leadingIcon = { DkIcon(icon = Icons.Default.Edit) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun StatusNote(
    modifier: Modifier = Modifier,
    state: SourceDetailsState,
) {
    when (val status = state.status) {
        SourceDetailsState.StatusUi.Active -> Unit

        SourceDetailsState.StatusUi.Pending -> DkInfoBox(
            modifier = modifier,
            text = stringResource(R.string.source_details_status_pending, state.peer.name),
        )

        is SourceDetailsState.StatusUi.Disabled -> DkInfoBox(
            modifier = modifier,
            title = stringResource(R.string.source_details_status_disabled),
            text = status.reason,
            tone = DkInfoTone.Alert,
        )
    }
}

@Composable
private fun PeerLine(
    modifier: Modifier = Modifier,
    state: SourceDetailsState,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (state.peer.online) colors.primary else colors.outline,
                    shape = CircleShape,
                ),
        )
        DkCaption(
            modifier = Modifier.weight(1f),
            text = stringResource(
                if (state.peer.online) R.string.source_details_peer_online
                else R.string.source_details_peer_offline,
                state.peer.name,
            ),
        )
        DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }
}

@Composable
private fun SendNowOffer(
    modifier: Modifier = Modifier,
    count: Int,
    bytes: Long,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(
                start = DkSpacing.lg,
                end = DkSpacing.sm,
                top = DkSpacing.md,
                bottom = DkSpacing.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = pluralStringResource(
                R.plurals.source_details_send_now_question,
                count,
                count,
                FileSize(bytes).formatted(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        DkGhostButton(
            text = stringResource(R.string.action_send),
            onClick = onSend,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun SourceDetailsScreenAutoUploadPreview() {
    FServerTheme {
        SourceDetailsScreenContent(
            state = SourceDetailsState.SampleAutoUpload,
            onIntent = {},
            navigateToActivity = {},
            navigateToFiles = {},
            navigateToDevice = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun SourceDetailsScreenSyncPreview() {
    FServerTheme {
        SourceDetailsScreenContent(
            state = SourceDetailsState.SampleSync,
            onIntent = {},
            navigateToActivity = {},
            navigateToFiles = {},
            navigateToDevice = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun SourceDetailsScreenOffloadPreview() {
    FServerTheme {
        SourceDetailsScreenContent(
            state = SourceDetailsState.SampleOffload,
            onIntent = {},
            navigateToActivity = {},
            navigateToFiles = {},
            navigateToDevice = {},
            navigateUp = {},
        )
    }
}
