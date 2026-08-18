package com.fserver.app.presentation.screens.discovery.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.info.model.NetworkInfo


@Immutable
sealed interface NetworkCardUi {
    data class Wifi(val name: String?) : NetworkCardUi
    data class Mobile(val name: String?) : NetworkCardUi

    /** Ethernet, or a link the core cannot characterise. Online, and nothing more to say. */
    data object Connected : NetworkCardUi
    data object Offline : NetworkCardUi
}

fun NetworkInfo?.toCardUi(): NetworkCardUi = when (this) {
    null -> NetworkCardUi.Offline
    is NetworkInfo.WiFi -> NetworkCardUi.Wifi(name = ssid)
    is NetworkInfo.Mobile -> NetworkCardUi.Mobile(name = name)
    NetworkInfo.Wired, NetworkInfo.Other -> NetworkCardUi.Connected
}

@Composable
fun NetworkCard(
    modifier: Modifier = Modifier,
    network: NetworkCardUi?,
    onFixClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    val fixable = onFixClick?.takeIf { network is NetworkCardUi.Wifi && network.name == null }

    val grantLabel = stringResource(R.string.network_redacted_action)

    val complete = when (network) {
        is NetworkCardUi.Wifi -> network.name != null
        is NetworkCardUi.Mobile, NetworkCardUi.Connected -> true
        null, NetworkCardUi.Offline -> false
    }

    val title = when (network) {
        NetworkCardUi.Connected -> stringResource(R.string.network_connected)

        null, NetworkCardUi.Offline -> stringResource(R.string.network_offline)

        is NetworkCardUi.Mobile -> network.name
            ?.let { stringResource(R.string.network_mobile, it) }
            ?: stringResource(R.string.network_mobile_unnamed)

        is NetworkCardUi.Wifi -> network.name
            ?.let { stringResource(R.string.network_wifi_named, it) }
            ?: stringResource(R.string.network_wifi_connected)
    }

    val hint = when (network) {
        NetworkCardUi.Connected -> null

        null, NetworkCardUi.Offline -> stringResource(R.string.discovery_network_hint_offline)

        is NetworkCardUi.Mobile -> stringResource(R.string.discovery_network_hint_mobile)

        is NetworkCardUi.Wifi -> when {
            network.name != null -> null
            fixable != null -> stringResource(R.string.network_redacted_hint_tappable)
            else -> stringResource(R.string.network_redacted_hint)
        }
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
            .then(
                if (fixable != null) {
                    Modifier
                        .clip(shape)
                        .clickable(onClickLabel = grantLabel, onClick = fixable)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val live = network != null && network != NetworkCardUi.Offline

            Box(
                modifier = Modifier
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

            when (network) {
                is NetworkCardUi.Wifi if network.name == null -> {
                    DkTag(stringResource(R.string.network_tag_hidden))
                }

                is NetworkCardUi.Wifi -> {
                    DkTag(stringResource(R.string.network_tag_private))
                }

                else -> {
                }
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
                NetworkCard(network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME))
                NetworkCard(network = NetworkCardUi.Wifi(name = null))
                NetworkCard(network = NetworkCardUi.Wifi(name = null), onFixClick = {})
                NetworkCard(network = NetworkCardUi.Connected)
                NetworkCard(network = NetworkCardUi.Mobile("LTE"))
                NetworkCard(network = NetworkCardUi.Offline)
            }
        }
    }
}
