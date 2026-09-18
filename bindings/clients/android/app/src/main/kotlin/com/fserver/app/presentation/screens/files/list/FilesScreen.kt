package com.fserver.app.presentation.screens.files.list

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.DkFab
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkFilterChip
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.files.list.composable.DeviceDetailsCard
import com.fserver.app.presentation.screens.files.list.composable.DeviceStrip
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.files.list.model.FilesUiEffect
import com.fserver.app.presentation.screens.source.request.shared.composable.SyncRequestBanner
import com.fserver.app.presentation.screens.source.setup.shared.rememberSourceFileOpener
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreview
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = koinViewModel(),
    navigateToActions: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: (String?) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToFolder: (String) -> Unit,
    navigateToSourceDetails: (String) -> Unit,
    navigateToDeviceSettings: (String) -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateToQrScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fileOpener = rememberSourceFileOpener()

    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is FilesUiEffect.OpenFile -> fileOpener.open(effect.file)
            }
        }
    }

    FilesScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToActions = navigateToActions,
        navigateToConnect = navigateToConnect,
        navigateToSourcePick = navigateToSourcePick,
        navigateToSyncRequests = navigateToSyncRequests,
        navigateToFolder = { navigateToFolder(it.path) },
        navigateToSourceDetails = navigateToSourceDetails,
        navigateToDeviceSettings = navigateToDeviceSettings,
        navigateToManualAddress = navigateToManualAddress,
        navigateToQrScan = navigateToQrScan,
    )
}

@Composable
private fun FilesScreenContent(
    modifier: Modifier = Modifier,
    state: FilesState,
    onIntent: (FilesIntent) -> Unit,
    navigateToActions: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: (String?) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToFolder: (SourcePreviewUi.File) -> Unit,
    navigateToSourceDetails: (String) -> Unit = {},
    navigateToDeviceSettings: (String) -> Unit,
    navigateToManualAddress: () -> Unit = {},
    navigateToQrScan: () -> Unit = {},
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                modifier = Modifier.alpha(if (state.expandedDevice != null) 0.35f else 1f),
                title = stringResource(R.string.files_title),
                actions = {
                    IconButton(onClick = navigateToActions) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DkFab(
                icon = Icons.Default.Add,
                label = stringResource(R.string.files_send_file),
                onClick = navigateToActions,
                visible = state.expandedDevice == null,
            )
        },
    ) { innerPadding ->
        FilesFeed(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = state,
            onIntent = onIntent,
            navigateToConnect = navigateToConnect,
            navigateToSourcePick = navigateToSourcePick,
            navigateToSyncRequests = navigateToSyncRequests,
            navigateToFolder = navigateToFolder,
        )
    }

    state.expandedDevice?.let { device ->
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
            onDismissRequest = { onIntent(FilesIntent.DeviceCollapsed) },
        ) {
            DeviceDetailsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DkSpacing.screenPadding),
                device = device,
                onClose = { onIntent(FilesIntent.DeviceCollapsed) },
                onFolderClick = { navigateToSourceDetails(it.id) },
                onAddFolder = { navigateToSourcePick(device.id) },
                onConfigure = { navigateToDeviceSettings(device.id) },
                onReconnectByAddress = navigateToManualAddress,
                onReconnectByQr = navigateToQrScan,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesFeed(
    modifier: Modifier = Modifier,
    state: FilesState,
    onIntent: (FilesIntent) -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: (String?) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToFolder: (SourcePreviewUi.File) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.networkWarning?.let { warning ->
            DkInfoBox(
                modifier = Modifier
                    .padding(
                        horizontal = DkSpacing.screenPadding,
                        vertical = DkSpacing.sm,
                    )
                    // Only the nameless-network one has a fix behind it; the rest are read-only.
                    .then(
                        if (warning.isActionable) {
                            Modifier.clickable {
                                onIntent(FilesIntent.NetworkWarningClicked)
                            }
                        } else {
                            Modifier
                        }
                    ),
                text = stringResource(warning.messageRes),
            )
        }

        if (state.showsSyncRequestHint) {
            state.syncRequest?.let { request ->
                SyncRequestBanner(
                    modifier = Modifier.padding(
                        horizontal = DkSpacing.screenPadding,
                        vertical = DkSpacing.sm,
                    ),
                    request = request,
                    waiting = state.syncRequestsWaiting,
                    onClick = navigateToSyncRequests,
                    onDismiss = { onIntent(FilesIntent.SyncRequestHintDismissed) },
                )
            }
        }

        if (state.devices.isNotEmpty()) {
            DeviceStrip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DkSpacing.md),
                devices = state.devices,
                selectedDeviceId = state.selectedDeviceId,
                onDeviceClick = { onIntent(FilesIntent.DeviceClicked(it)) },
                onDeviceLongClick = { onIntent(FilesIntent.DeviceExpanded(it)) },
            )
        }

        if (state.entries == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                DkInlineSpinner()
            }
        } else {
            AnimatedContent(
                targetState = state.selectedDevice,
                contentKey = { it?.id },
            ) { selected ->
                if (selected != null) {
                    SelectionSummary(
                        modifier = Modifier.padding(start = DkSpacing.screenPadding),
                        device = selected,
                        onClear = { onIntent(FilesIntent.FilterCleared) },
                    )
                }
            }

            if (state.showsFilters) {
                FilterChips(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    filter = state.filter,
                    onSelect = { onIntent(FilesIntent.FilterSelected(it)) },
                )
            }

            if (state.entries.preview.isEmpty) {
                FilesEmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    reason = state.emptyReason,
                    deviceName = state.feedDevice?.name,
                    navigateToConnect = navigateToConnect,
                    navigateToSourcePick = { navigateToSourcePick(null) },
                )
            } else {
                PullToRefreshBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isRefreshing = state.isSyncing,
                    onRefresh = { onIntent(FilesIntent.RefreshRequested) },
                ) {
                    SourcePreview(
                        modifier = Modifier.fillMaxSize(),
                        preview = state.entries.preview,
                        onFileClick = { entry ->
                            if (entry.kind == FileKindUi.Folder) {
                                navigateToFolder(entry)
                            } else {
                                onIntent(FilesIntent.EntryClicked(entry.id))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    modifier: Modifier = Modifier,
    filter: FilesState.FilterUi,
    onSelect: (FilesState.FilterUi) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkFilterChip(
            text = stringResource(R.string.files_filter_all),
            selected = filter == FilesState.FilterUi.All,
            onClick = { onSelect(FilesState.FilterUi.All) },
            icon = Icons.AutoMirrored.Filled.ViewList,
        )
        DkFilterChip(
            text = stringResource(R.string.files_filter_local),
            selected = filter == FilesState.FilterUi.Local,
            onClick = { onSelect(FilesState.FilterUi.Local) },
            icon = Icons.Default.Smartphone,
        )
        DkFilterChip(
            text = stringResource(R.string.files_filter_cloud),
            selected = filter == FilesState.FilterUi.Cloud,
            onClick = { onSelect(FilesState.FilterUi.Cloud) },
            icon = Icons.Default.Cloud,
        )
    }
}

@Composable
private fun SelectionSummary(
    modifier: Modifier = Modifier,
    device: FilesState.DeviceUi,
    onClear: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.files_device_items, device.name, device.itemCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DkGhostButton(text = stringResource(R.string.action_reset), onClick = onClear)
    }
}

@Composable
private fun FilesEmptyState(
    modifier: Modifier = Modifier,
    reason: FilesState.EmptyReasonUi,
    deviceName: String?,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkPlaceholderBox(
            modifier = Modifier.height(120.dp),
            label = stringResource(R.string.files_empty_illustration),
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.xl),
            text = when (reason) {
                FilesState.EmptyReasonUi.NoDeviceFiles -> stringResource(
                    R.string.files_empty_device_title,
                    deviceName.orEmpty(),
                )

                else -> stringResource(reason.titleRes)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.sm),
            text = stringResource(reason.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        when (reason) {
            FilesState.EmptyReasonUi.NoDevices -> {
                DkPrimaryButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DkSpacing.xl),
                    text = stringResource(R.string.action_connect),
                    onClick = navigateToConnect,
                )
                DkGhostButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DkSpacing.sm),
                    text = stringResource(R.string.fork_send_title),
                    onClick = navigateToSourcePick,
                )
            }

            FilesState.EmptyReasonUi.NoFiles -> {
                DkPrimaryButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DkSpacing.xl),
                    text = stringResource(R.string.fork_send_title),
                    onClick = navigateToSourcePick,
                )
                DkGhostButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DkSpacing.sm),
                    text = stringResource(R.string.action_connect),
                    onClick = navigateToConnect,
                )
            }

            // A filter is the reason the feed is empty, and the chips above it are what undoes
            // that, so the placeholder says so and offers nothing of its own.
            else -> Unit
        }
    }
}

@get:StringRes
private val FilesState.EmptyReasonUi.titleRes: Int
    get() = when (this) {
        FilesState.EmptyReasonUi.NoDevices -> R.string.files_empty_title
        FilesState.EmptyReasonUi.NoFiles -> R.string.files_empty_no_files_title
        FilesState.EmptyReasonUi.NoLocalFiles -> R.string.files_empty_local_title
        FilesState.EmptyReasonUi.NoCloudFiles -> R.string.files_empty_cloud_title
        FilesState.EmptyReasonUi.NoDeviceFiles -> R.string.files_empty_device_title
    }

@get:StringRes
private val FilesState.EmptyReasonUi.bodyRes: Int
    get() = when (this) {
        FilesState.EmptyReasonUi.NoDevices -> R.string.files_empty_body
        FilesState.EmptyReasonUi.NoFiles -> R.string.files_empty_no_files_body
        FilesState.EmptyReasonUi.NoLocalFiles -> R.string.files_empty_local_body
        FilesState.EmptyReasonUi.NoCloudFiles -> R.string.files_empty_cloud_body
        FilesState.EmptyReasonUi.NoDeviceFiles -> R.string.files_empty_device_body
    }

@get:StringRes
private val FilesState.NetworkWarningUi.messageRes: Int
    get() = when (this) {
        FilesState.NetworkWarningUi.NoNetwork -> R.string.files_network_offline
        FilesState.NetworkWarningUi.NoLocalNetwork -> R.string.files_network_no_lan
        FilesState.NetworkWarningUi.DifferentNetwork -> R.string.files_network_other
        FilesState.NetworkWarningUi.UnnamedNetwork -> R.string.files_network_unnamed
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                devices = FilesState.SampleDevices,
                entries = FilesState.SampleEntries,
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}

@Preview(name = "Device selected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenFilteredPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                devices = FilesState.SampleDevices,
                selectedDeviceId = "server",
                entries = FilesState.SampleEntries.copy(deviceId = "server"),
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}

@Preview(name = "Filter with no matches", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenEmptyFilterPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                devices = FilesState.SampleDevices,
                filter = FilesState.FilterUi.Cloud,
                entries = FilesState.FeedUi(
                    preview = SourcePreviewUi.PlainList(emptyList()),
                    filter = FilesState.FilterUi.Cloud,
                    deviceId = null,
                ),
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}

@Preview(name = "Device expanded", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenExpandedPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                devices = FilesState.SampleDevices,
                entries = FilesState.SampleEntries,
                expandedDevice = FilesState.sampleDetailsOf(FilesState.SampleDevices[1]),
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}

@Preview(name = "Off the home network", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenNetworkWarningPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                devices = FilesState.SampleDevices,
                entries = FilesState.SampleEntries,
                networkWarning = FilesState.NetworkWarningUi.DifferentNetwork,
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenEmptyPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                entries = FilesState.FeedUi(
                    preview = SourcePreviewUi.PlainList(emptyList()),
                    filter = FilesState.FilterUi.All,
                    deviceId = null,
                ),
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
            navigateToFolder = {},
            navigateToDeviceSettings = {},
        )
    }
}
