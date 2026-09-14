package com.fserver.app.presentation.screens.discovery.connect.composable

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSurfacePreview
import com.fserver.app.presentation.composable.model.RequirementRowUi
import com.fserver.app.presentation.composable.model.descriptionRes
import com.fserver.app.presentation.composable.model.titleRes
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.app.presentation.permission.RequirementResolver
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.TransportKind

/**
 * One method and what it is still waiting on.
 *
 * Only unmet requirements appear: `RequirementReport` reports nothing else, and a list of things
 * already granted would be reassurance rather than information.
 */
@Composable
fun TransportKindSheet(
    modifier: Modifier = Modifier,
    setup: ConnectState.MethodSetupUi,
    resolver: RequirementResolver?,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Text(
            text = stringResource(setup.method.titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DkCaption(text = stringResource(setup.method.descriptionRes))

        if (setup.solvable.isNotEmpty()) {
            DkSectionLabel(
                text = stringResource(R.string.method_requirements_label),
                trailing = {
                    DkCaption(
                        text = pluralStringResource(
                            R.plurals.method_requirements_left,
                            setup.solvable.size,
                            setup.solvable.size,
                        )
                    )
                },
            )

            setup.solvable.forEachIndexed { index, row ->
                RequirementRow(
                    row = row,
                    onGrant = { action ->
                        resolver?.resolve(action)
                    },
                )
                if (index != setup.solvable.lastIndex) {
                    DkFadingDivider()
                }
            }
        }

        if (setup.blockers.isNotEmpty()) {
            DkSectionLabel(text = stringResource(R.string.method_blockers_label))

            setup.blockers.forEach { row ->
                RequirementRow(row = row, onGrant = null)
            }
        }

        if (setup.solvable.isNotEmpty()) {
            DkInfoBox(text = stringResource(R.string.method_setup_note))

            DkPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.method_grant_rest),
                enabled = setup.firstAction != null,
                onClick = {
                    setup.firstAction?.let {
                        resolver?.resolve(it)
                    }
                },
            )
        }
    }
}

@Composable
private fun RequirementRow(
    row: RequirementRowUi,
    onGrant: ((RequirementAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val action = row.action
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = if (row.resolvable) {
                Icons.Default.RadioButtonUnchecked
            } else {
                Icons.Default.ErrorOutline
            },
            contentDescription = null,
            tint = if (row.resolvable) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DkCaption(text = stringResource(row.detailRes))
        }
        if (action != null && onGrant != null) {
            DkSecondaryButton(
                text = stringResource(R.string.method_grant),
                onClick = { onGrant(action) },
            )
        }
    }
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
