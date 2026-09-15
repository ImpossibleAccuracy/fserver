package com.fserver.app.presentation.screens.discovery.connect.composable

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.RequirementsSheetContent
import com.fserver.app.presentation.composable.model.RequirementRowUi
import com.fserver.app.presentation.composable.model.descriptionRes
import com.fserver.app.presentation.composable.model.titleRes
import com.fserver.app.presentation.designkit.DkSurfacePreview
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.app.presentation.permission.RequirementResolver
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.TransportKind

/**
 * One method and what it is still waiting on.
 *
 * Same rows as the failure-raised sheet, opened on purpose rather than by a broken call, so the
 * header names the method instead of the failure.
 */
@Composable
fun TransportKindSheet(
    modifier: Modifier = Modifier,
    setup: ConnectState.MethodSetupUi,
    resolver: RequirementResolver?,
) {
    RequirementsSheetContent(
        modifier = modifier,
        title = stringResource(setup.method.titleRes),
        description = stringResource(setup.method.descriptionRes),
        solvable = setup.solvable,
        blockers = setup.blockers,
        firstAction = setup.firstAction,
        resolver = resolver,
    )
}

@SuppressLint("InlinedApi")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TransportKindSheetPreview() {
    FServerTheme {
        DkSurfacePreview {
            TransportKindSheet(
                setup = ConnectState.MethodSetupUi(
                    method = TransportKind.NearbyConnections,
                    solvable = listOf(
                        RequirementRowUi(
                            R.string.requirement_permission_nearby_devices_title,
                            R.string.requirement_permission_nearby_devices_description,
                            RequirementAction.RequestPermissions(
                                listOf(Manifest.permission.BLUETOOTH_SCAN)
                            ),
                        ),
                    ),
                    blockers = emptyList(),
                ),
                resolver = null,
            )
        }
    }
}

@Preview(name = "Blocked", showBackground = true, widthDp = 360)
@Composable
private fun TransportKindSheetBlockedPreview() {
    FServerTheme {
        DkSurfacePreview {
            TransportKindSheet(
                setup = ConnectState.MethodSetupUi(
                    method = TransportKind.MulticastDns,
                    solvable = emptyList(),
                    blockers = listOf(
                        RequirementRowUi(
                            R.string.requirement_network_title,
                            R.string.requirement_network_multicast_description,
                        ),
                    ),
                ),
                resolver = null,
            )
        }
    }
}
