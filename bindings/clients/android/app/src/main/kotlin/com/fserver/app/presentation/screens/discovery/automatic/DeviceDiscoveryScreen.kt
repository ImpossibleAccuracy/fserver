package com.fserver.app.presentation.screens.discovery.automatic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.screens.discovery.shared.NetworkCardUi
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.titleRes
import com.fserver.app.presentation.permission.RequirementResolver
import com.fserver.app.presentation.permission.rememberRequirementResolver
import com.fserver.app.presentation.screens.discovery.automatic.composable.TransportKindSheet
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryState
import com.fserver.app.presentation.screens.discovery.shared.NetworkCard
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.TransportKind
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceDiscoveryScreen(
    viewModel: DeviceDiscoveryViewModel = koinViewModel(),
    navigateToPairing: (deviceId: String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = rememberRequirementResolver(onResolved = viewModel::onResumed)

    // Permissions can be granted or revoked from outside the app, so the reports are re-read
    // every time this screen comes back rather than cached from when it was opened.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    DeviceDiscoveryScreen(
        state = state,
        resolver = resolver,
        onIntent = viewModel::onIntent,
        navigateToPairing = navigateToPairing,
        navigateUp = navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDiscoveryScreen(
    state: DeviceDiscoveryState,
    resolver: RequirementResolver,
    onIntent: (DeviceDiscoveryIntent) -> Unit,
    navigateToPairing: (deviceId: String) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(
                    when {
                        state.runningCount > 0 -> R.string.discovery_title_searching
                        state.isSearching -> R.string.discovery_title_results
                        else -> R.string.discovery_title_idle
                    }
                ),
                onBack = navigateUp,
                actions = {
                    // Nothing running, nothing to stop — the per-method retry takes over there.
                    if (state.runningCount > 0) {
                        DkGhostButton(
                            text = stringResource(R.string.action_stop),
                            onClick = { onIntent(DeviceDiscoveryIntent.StopSearchClicked) },
                        )
                    }
                },
            )
        },
        bottomBar = { DiscoveryBottomBar(state = state, onIntent = onIntent) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item(key = "network") {
                NetworkCard(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    network = state.network,
                    onFixClick = state.networkAction?.let { action ->
                        { resolver.resolve(action) }
                    },
                )
            }

            item(key = "methods-label") {
                DkSectionLabel(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = stringResource(R.string.discovery_methods_label),
                    trailing = {
                        DkCaption(
                            text = if (state.isSearching) {
                                stringResource(
                                    R.string.discovery_methods_running,
                                    state.runningCount,
                                    state.methods.size,
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.discovery_methods_selected,
                                    state.selectedCount,
                                    state.selectedCount,
                                )
                            }
                        )
                    },
                )
            }

            itemsIndexed(state.methods) { index, method ->
                MethodRow(method = method, searching = state.isSearching, onIntent = onIntent)
                if (index != state.methods.lastIndex) {
                    DkFadingDivider()
                }
            }

            if (state.isSearching) {
                item(key = "found-label") {
                    DkSectionLabel(
                        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                        text = stringResource(R.string.discovery_found_label),
                        trailing = { DkCaption(text = state.devices.size.toString()) },
                    )
                }

                if (state.devices.isEmpty()) {
                    item(key = "found-placeholder") { ResultPlaceholders() }
                } else {
                    items(state.devices, key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            onClick = {
                                navigateToPairing(device.id)
                            }
                        )
                        DkFadingDivider()
                    }
                }
            }
        }
    }

    if (state.methodSetup != null) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(DeviceDiscoveryIntent.MethodSetupDismissed) },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            TransportKindSheet(
                setup = state.methodSetup,
                resolver = resolver,
            )
        }
    }
}

@Composable
private fun DiscoveryBottomBar(
    state: DeviceDiscoveryState,
    onIntent: (DeviceDiscoveryIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DkSpacing.screenPadding)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.isSearching) {
            CenteredCaption(
                text = when {
                    state.runningCount == 0 -> stringResource(R.string.discovery_finished_hint)
                    state.devices.isEmpty() -> stringResource(R.string.discovery_first_devices_hint)
                    else -> stringResource(R.string.discovery_continues_hint)
                }
            )
            return@Column
        }

        if (state.selectedCount == 0) {
            CenteredCaption(text = stringResource(R.string.discovery_start_hint))
        }

        DkPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            text = if (state.selectedCount == 0) {
                stringResource(R.string.discovery_start_search_empty)
            } else {
                pluralStringResource(
                    R.plurals.discovery_start_search,
                    state.selectedCount,
                    state.selectedCount,
                )
            },
            enabled = state.selectedCount > 0,
            onClick = { onIntent(DeviceDiscoveryIntent.StartSearchClicked) },
        )
    }
}

/**
 * A method row is the same shape in both phases; what changes is whether the leading slot is a
 * choice (a checkbox) or a report (a spinner and a count).
 */
@Composable
private fun MethodRow(
    method: DeviceDiscoveryState.MethodUi,
    searching: Boolean,
    onIntent: (DeviceDiscoveryIntent) -> Unit,
) {
    val openSetup = { onIntent(DeviceDiscoveryIntent.MethodClicked(method.method)) }
    val toggle = { onIntent(DeviceDiscoveryIntent.MethodToggled(method.method)) }
    val start = { onIntent(DeviceDiscoveryIntent.MethodStartRequested(method.method)) }

    if (searching) {
        DkListRow(
            title = stringResource(method.method.titleRes),
            // A method that has run is part of this search whether or not it is still going;
            // only one that never joined is drawn as absent.
            dimmed = !method.isScanning && !method.hasRun,
            onClick = when {
                method.isScanning -> null
                method.isReady -> start
                else -> openSetup
            },
            leading = {
                if (method.isScanning) {
                    DkInlineSpinner()
                } else {
                    IdleDot()
                }
            },
            trailing = {
                if (method.isScanning) {
                    DkMonoCaption(text = method.foundCount.toString())
                } else {
                    Text(
                        text = stringResource(
                            if (method.hasRun) {
                                R.string.discovery_method_retry
                            } else {
                                R.string.discovery_method_add
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    } else {
        DkListRow(
            title = stringResource(method.method.titleRes),
            subtitle = when {
                method.isBlocked -> stringResource(R.string.discovery_method_blocked)
                method.isReady -> stringResource(R.string.discovery_method_ready_subtitle)
                method.unmetCount != null -> pluralStringResource(
                    R.plurals.discovery_method_needs_permissions,
                    method.unmetCount,
                    method.unmetCount,
                )

                else -> null
            },
            dimmed = method.isBlocked,
            onClick = if (method.isReady) toggle else openSetup,
            leading = {
                Checkbox(
                    modifier = Modifier.size(20.dp),
                    checked = method.selected,
                    enabled = method.isReady,
                    onCheckedChange = { toggle() },
                )
            },
            trailing = {
                if (method.isReady) {
                    DkTag(
                        stringResource(R.string.discovery_method_ready),
                        style = DkTagStyle.Accent
                    )
                } else {
                    DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                }
            },
        )
    }
}

@Composable
private fun DeviceRow(
    device: DeviceDiscoveryState.DeviceUi,
    onClick: () -> Unit,
) {
    DkListRow(
        title = device.name,
        subtitle = device.address,
        subtitleStyle = DkType.mono,
        onClick = onClick,
        leading = { DkThumbnail(icon = device.kind.icon) },
        trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
    )
}

/** The slot a method that is not running would occupy, drawn so the list keeps its shape. */
@Composable
private fun IdleDot() {
    Box(
        modifier = Modifier
            .size(11.dp)
            .dkDashedBorder(
                color = MaterialTheme.colorScheme.outline,
                cornerRadius = 6.dp,
                dash = 2.dp,
            )
    )
}

/** Where results will land, so an empty search reads as "not yet" rather than "nothing". */
@Composable
private fun ResultPlaceholders() {
    Column(
        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .alpha(if (index == 0) 1f else 0.5f)
                    .dkDashedBorder(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun CenteredCaption(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun method(
    method: TransportKind,
    selected: Boolean = false,
    isScanning: Boolean = false,
    hasRun: Boolean = false,
    foundCount: Int = 0,
    unmetCount: Int? = 0,
    isBlocked: Boolean = false,
) = DeviceDiscoveryState.MethodUi(
    method = method,
    selected = selected,
    isScanning = isScanning,
    hasRun = hasRun,
    foundCount = foundCount,
    unmetCount = unmetCount,
    isBlocked = isBlocked,
)

private val NothingSelected = listOf(
    method(TransportKind.MulticastDns),
    method(TransportKind.SubnetScan, unmetCount = 1),
    method(TransportKind.NearbyConnections, unmetCount = 2),
)

private val TwoReady = listOf(
    method(TransportKind.MulticastDns, selected = true),
    method(TransportKind.SubnetScan, unmetCount = 1),
    method(TransportKind.NearbyConnections, selected = true),
)

@Preview(name = "Nothing selected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryIdlePreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = NetworkCardUi.Wifi(name = null),
                methods = NothingSelected,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToPairing = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Two ready", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryReadyPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME),
                methods = TwoReady,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToPairing = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Searching — empty", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoverySearchingPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME),
                methods = TwoReady.map { it.copy(isScanning = it.selected, hasRun = it.selected) },
                isSearching = true,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToPairing = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Searching — results", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryResultsPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME),
                methods = TwoReady.map {
                    it.copy(isScanning = it.selected, hasRun = it.selected, foundCount = 1)
                },
                devices = DeviceDiscoveryState.SampleDevices,
                isSearching = true,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToPairing = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Searching — finished", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryFinishedPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME),
                methods = TwoReady.map { it.copy(hasRun = it.selected, foundCount = 1) },
                devices = DeviceDiscoveryState.SampleDevices,
                isSearching = true,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToPairing = {},
            navigateUp = {},
        )
    }
}
