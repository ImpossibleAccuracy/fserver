package com.fserver.app.presentation.screens.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.DkFabMenu
import com.fserver.app.presentation.composable.DkFabMenuItem
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.dashboard.composable.LinksSection
import com.fserver.app.presentation.screens.dashboard.composable.NetworkEmptyState
import com.fserver.app.presentation.screens.dashboard.composable.NetworkSection
import com.fserver.app.presentation.screens.dashboard.composable.StorageSection
import com.fserver.app.presentation.screens.dashboard.model.DashboardIntent
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
    navigateToFiles: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToSourceDetails: (String) -> Unit,
    navigateToDeviceSettings: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DashboardScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToFiles = navigateToFiles,
        navigateToConnect = navigateToConnect,
        navigateToSourcePick = navigateToSourcePick,
        navigateToSyncRequests = navigateToSyncRequests,
        navigateToSourceDetails = navigateToSourceDetails,
        navigateToDeviceSettings = navigateToDeviceSettings,
    )
}

/** Phone storage, the sources it syncs, and the devices on the other end of them. */
@Composable
private fun DashboardScreenContent(
    modifier: Modifier = Modifier,
    state: DashboardState,
    onIntent: (DashboardIntent) -> Unit,
    navigateToFiles: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToSourceDetails: (String) -> Unit = {},
    navigateToDeviceSettings: (String) -> Unit = {},
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.dashboard_title),
                actions = {
                    TextButton(onClick = navigateToFiles) {
                        Text(text = stringResource(R.string.dashboard_all_files))
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DkFabMenu(
                items = listOf(
                    DkFabMenuItem(
                        icon = Icons.Default.SwapHoriz,
                        label = stringResource(R.string.fork_connect_title),
                        onClick = navigateToConnect,
                    ),
                    DkFabMenuItem(
                        icon = Icons.Default.Upload,
                        label = stringResource(R.string.fork_send_title),
                        onClick = navigateToSourcePick,
                    ),
                ),
                contentDescription = stringResource(R.string.files_actions),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            Banners(
                state = state,
                onIntent = onIntent,
                navigateToSyncRequests = navigateToSyncRequests,
            )

            val section = Modifier.padding(horizontal = DkSpacing.screenPadding)

            AnimatedVisibility(visible = state.hasLinks && state.storage != null) {
                Column {
                    DkSectionLabel(
                        modifier = section,
                        text = stringResource(R.string.dashboard_storage),
                    )
                    state.storage?.let { StorageSection(modifier = section, storage = it) }
                }
            }

            AnimatedVisibility(visible = state.hasLinks) {
                Column {
                    DkSectionLabel(
                        modifier = section.padding(top = DkSpacing.sm),
                        text = stringResource(R.string.dashboard_links),
                    )
                    LinksSection(
                        links = state.links,
                        onLinkClick = navigateToSourceDetails,
                    )
                }
            }

            DkSectionLabel(
                modifier = section.padding(top = DkSpacing.sm),
                text = stringResource(R.string.dashboard_network),
            )
            if (state.hasLinks) {
                NetworkSection(
                    network = state.network,
                    discovering = state.discovering,
                    devices = state.devices,
                    onlineDevices = state.onlineDevices,
                    onConnect = navigateToConnect,
                    onDeviceClick = navigateToDeviceSettings,
                )
            } else {
                NetworkEmptyState(modifier = section, onConnect = navigateToConnect)
            }

            // Room for the FAB, so the last row can scroll out from under it.
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun Banners(
    state: DashboardState,
    onIntent: (DashboardIntent) -> Unit,
    navigateToSyncRequests: () -> Unit,
) {
    // Held past the moment they clear, so each box has something to draw while it collapses.
    val warning = rememberLastNotNull(state.networkWarning)
    AnimatedVisibility(visible = state.networkWarning != null) {
        if (warning == null) return@AnimatedVisibility

        DkInfoBox(
            modifier = Modifier
                .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm)
                // Only the nameless-network one has a fix behind it; the rest are read-only.
                .then(
                    if (warning.isActionable) {
                        Modifier.clickable { onIntent(DashboardIntent.NetworkWarningClicked) }
                    } else {
                        Modifier
                    }
                ),
            text = stringResource(warning.messageRes),
        )
    }

    val waiting = rememberLastNotNull(state.syncRequestsWaiting.takeIf { it > 0 })
    AnimatedVisibility(visible = state.syncRequestsWaiting > 0) {
        if (waiting == null) return@AnimatedVisibility

        SyncRequestsRow(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
            waiting = waiting,
            onClick = navigateToSyncRequests,
        )
    }
}

@Composable
private fun SyncRequestsRow(
    modifier: Modifier = Modifier,
    waiting: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = DkSpacing.lg, vertical = DkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = Icons.Default.SyncAlt,
            contentDescription = null,
            tint = colors.primary,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = pluralStringResource(R.plurals.dashboard_sync_requests, waiting, waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }
}

/** [value], or the last non-null one it had: what an exit animation keeps drawing. */
@Composable
private fun <T : Any> rememberLastNotNull(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

@get:StringRes
private val DashboardState.NetworkWarningUi.messageRes: Int
    get() = when (this) {
        DashboardState.NetworkWarningUi.NoNetwork -> R.string.files_network_offline
        DashboardState.NetworkWarningUi.NoLocalNetwork -> R.string.files_network_no_lan
        DashboardState.NetworkWarningUi.DifferentNetwork -> R.string.files_network_other
        DashboardState.NetworkWarningUi.UnnamedNetwork -> R.string.files_network_unnamed
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun DashboardScreenPreview() {
    FServerTheme {
        DashboardScreenContent(
            state = DashboardState.Sample,
            onIntent = {},
            navigateToFiles = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
        )
    }
}

@Preview(name = "No sources", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DashboardScreenEmptyPreview() {
    FServerTheme {
        DashboardScreenContent(
            state = DashboardState(
                storage = DashboardState.Sample.storage,
                network = DashboardState.Sample.network,
            ),
            onIntent = {},
            navigateToFiles = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
        )
    }
}
