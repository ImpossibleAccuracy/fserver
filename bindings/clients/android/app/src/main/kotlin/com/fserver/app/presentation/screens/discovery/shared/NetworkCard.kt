package com.fserver.app.presentation.screens.discovery.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSurfacePreview
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.composable.shared.NetworkCardUi
import com.fserver.app.presentation.theme.FServerTheme

/**
 * What the phone is connected to, drawn the same way everywhere the connection flow shows it.
 *
 * The outline carries the meaning: solid when the card states a fact, dashed when part of it is
 * still withheld — an unnamed Wi-Fi, or no network at all.
 */
@Composable
fun NetworkCard(
    network: NetworkCardUi?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    val live = network != null && network != NetworkCardUi.Offline
    val complete = network is NetworkCardUi.Wifi && network.name != null ||
            network is NetworkCardUi.Mobile

    val title = when (network) {
        null, NetworkCardUi.Offline -> stringResource(R.string.network_offline)
        is NetworkCardUi.Mobile -> stringResource(R.string.network_mobile, network.name)
        is NetworkCardUi.Wifi -> network.name
            ?.let { stringResource(R.string.network_wifi_named, it) }
            ?: stringResource(R.string.network_wifi_connected)
    }

    val hint = when (network) {
        null, NetworkCardUi.Offline -> stringResource(R.string.discovery_network_hint_offline)
        is NetworkCardUi.Mobile -> stringResource(R.string.discovery_network_hint_mobile)
        is NetworkCardUi.Wifi ->
            if (network.name == null) stringResource(R.string.network_redacted_hint) else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (complete) {
                    Modifier.border(1.dp, colors.outlineVariant, shape)
                } else {
                    Modifier.dkDashedBorder(colors.outlineVariant)
                }
            )
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(7.dp)
                    .background(
                        if (live) colors.primary else colors.onSurfaceVariant,
                        CircleShape,
                    )
            )
            Text(
                modifier = Modifier.padding(start = DkSpacing.sm),
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            when {
                network is NetworkCardUi.Wifi && network.name == null ->
                    DkTag(stringResource(R.string.network_tag_hidden))

                network is NetworkCardUi.Wifi ->
                    DkTag(stringResource(R.string.network_tag_private))

                else -> Unit
            }
        }

        if (hint != null) {
            DkCaption(text = hint)
        }
    }
}

@Preview
@Composable
private fun NetworkCardPreview() {
    FServerTheme {
        DkSurfacePreview {
            Column(
                modifier = Modifier.padding(DkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                NetworkCard(NetworkCardUi.Wifi(SampleData.NETWORK_NAME))
                NetworkCard(NetworkCardUi.Wifi(name = null))
                NetworkCard(NetworkCardUi.Mobile("LTE"))
                NetworkCard(NetworkCardUi.Offline)
            }
        }
    }
}
