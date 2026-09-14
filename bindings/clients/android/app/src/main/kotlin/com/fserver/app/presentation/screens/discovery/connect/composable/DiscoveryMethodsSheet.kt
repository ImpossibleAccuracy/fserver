package com.fserver.app.presentation.screens.discovery.connect.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.titleRes
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSurfacePreview
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectIntent
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.TransportKind

/**
 * Every way of looking for a device, and what each still needs.
 *
 * A row is the method's switch: tapping a ready one starts it, tapping it again stops it. There is
 * no separate "start the search" — a method that is ticked but not running is a state the user has
 * to press a second button to leave, and the only thing it ever bought was starting several at
 * once, which tapping them does just as well.
 *
 * It is a sheet rather than a screen because what it produces — devices — lands in the list
 * underneath it. Results are never drawn here: the user closes the sheet and reads the answer
 * where every other device already is.
 */
@Composable
fun DiscoveryMethodsSheet(
    state: ConnectState,
    onIntent: (ConnectIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        DkSectionLabel(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            text = stringResource(R.string.discovery_methods_label),
            trailing = {
                if (state.isScanning) {
                    DkCaption(
                        text = stringResource(
                            R.string.discovery_methods_running,
                            state.runningCount,
                            state.methods.size,
                        )
                    )
                }
            },
        )

        state.methods.forEachIndexed { index, method ->
            MethodRow(method = method, onIntent = onIntent)
            if (index != state.methods.lastIndex) {
                DkFadingDivider()
            }
        }

        DkCaption(
            modifier = Modifier
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(top = DkSpacing.md),
            text = stringResource(R.string.discovery_methods_hint),
        )
    }
}

/**
 * The row is the same shape whichever state the method is in; what changes is whether the
 * trailing slot reports a scan or offers to set one up.
 */
@Composable
private fun MethodRow(
    method: ConnectState.MethodUi,
    onIntent: (ConnectIntent) -> Unit,
) {
    val toggle = { onIntent(ConnectIntent.MethodToggled(method.method)) }
    val openSetup = { onIntent(ConnectIntent.MethodClicked(method.method)) }

    DkListRow(
        title = stringResource(method.method.titleRes),
        subtitle = when {
            method.isScanning -> null
            method.isBlocked -> stringResource(R.string.discovery_method_blocked)
            method.isReady -> stringResource(R.string.discovery_method_ready_subtitle)
            method.unmetCount != null -> pluralStringResource(
                R.plurals.discovery_method_needs_permissions,
                method.unmetCount,
                method.unmetCount,
            )

            else -> null
        },
        // A method that has run is part of this search whether or not it is still going; only one
        // that can never join is drawn as absent.
        dimmed = method.isBlocked,
        onClick = if (method.isReady) toggle else openSetup,
        leading = {
            if (method.isScanning) DkInlineSpinner() else IdleDot()
        },
        trailing = {
            when {
                // The count is the reason to keep it running, so it is what the row reports —
                // and it stays after the scan ends, as what that run turned up.
                method.isScanning || method.hasRun ->
                    DkMonoCaption(text = method.foundCount.toString())

                method.isReady ->
                    DkTag(stringResource(R.string.discovery_method_ready), style = DkTagStyle.Accent)

                else -> DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        },
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

private fun method(
    method: TransportKind,
    isScanning: Boolean = false,
    hasRun: Boolean = false,
    foundCount: Int = 0,
    unmetCount: Int? = 0,
    isBlocked: Boolean = false,
) = ConnectState.MethodUi(
    method = method,
    isScanning = isScanning,
    hasRun = hasRun,
    foundCount = foundCount,
    unmetCount = unmetCount,
    isBlocked = isBlocked,
)

private val Idle = listOf(
    method(TransportKind.MulticastDns),
    method(TransportKind.SubnetScan, unmetCount = 1),
    method(TransportKind.NearbyConnections),
)

@Preview(name = "Idle", showBackground = true, widthDp = 360)
@Composable
private fun DiscoveryMethodsSheetPreview() {
    FServerTheme {
        DkSurfacePreview {
            DiscoveryMethodsSheet(
                state = ConnectState(methods = Idle),
                onIntent = {},
            )
        }
    }
}

@Preview(name = "Scanning", showBackground = true, widthDp = 360)
@Composable
private fun DiscoveryMethodsSheetScanningPreview() {
    FServerTheme {
        DkSurfacePreview {
            DiscoveryMethodsSheet(
                state = ConnectState(
                    methods = listOf(
                        method(TransportKind.MulticastDns, isScanning = true, hasRun = true, foundCount = 2),
                        method(TransportKind.SubnetScan, unmetCount = 1),
                        method(TransportKind.NearbyConnections, hasRun = true, foundCount = 0),
                    ),
                ),
                onIntent = {},
            )
        }
    }
}
