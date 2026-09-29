package com.fserver.app.presentation.screens.source.request.composable

import com.fserver.app.presentation.designkit.DkStatusDot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkSettingsRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceEndpoints
import com.fserver.app.presentation.screens.source.shared.model.SourceEndpointUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind

@Composable
fun SyncRequestDetailsPage(
    modifier: Modifier = Modifier,
    request: SyncRequestUi,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = DkSpacing.lg, bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkCard(modifier = Modifier.padding(horizontal = DkSpacing.screenPadding)) {
            SourceEndpoints(
                origin = SourceEndpointUi(
                    name = request.deviceName,
                    detail = request.originPath,
                    deviceKind = request.deviceKind,
                ),
                target = SourceEndpointUi(
                    name = stringResource(R.string.sync_request_route_this_device),
                    deviceKind = DeviceKind.Phone,
                ),
                mode = request.mode,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.sync_request_heading, request.deviceName),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.sync_request_body, request.deviceName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            DkValueRow(
                title = stringResource(R.string.sync_request_source),
                value = request.label,
            )
            DkFadingDivider()
            DkValueRow(
                title = stringResource(R.string.sync_request_mode),
                value = stringResource(request.mode.titleRes),
            )
            DkFadingDivider()
            DkValueRow(
                title = stringResource(R.string.sync_request_volume),
                value = request.volumeLabel(),
            )
            if (request.fingerprint != null) {
                DkFadingDivider()
                FingerprintRow(fingerprint = request.fingerprint)
            }
        }
    }
}

@Composable
private fun FingerprintRow(
    modifier: Modifier = Modifier,
    fingerprint: String,
) {
    DkSettingsRow(
        modifier = modifier,
        title = stringResource(R.string.sync_request_fingerprint),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkStatusDot(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = fingerprint,
                    style = DkType.monoLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Composable
private fun SyncRequestUi.volumeLabel(): String {
    if (files == null || bytes == null) return stringResource(R.string.sync_request_volume_unknown)

    return stringResource(
        R.string.sync_request_volume_value,
        pluralStringResource(R.plurals.sync_request_volume_files, files, files),
        FileSize(bytes).formatted(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SyncRequestDetailsPagePreview() {
    FServerTheme {
        SyncRequestDetailsPage(request = SyncRequestState.Sample.request!!)
    }
}
