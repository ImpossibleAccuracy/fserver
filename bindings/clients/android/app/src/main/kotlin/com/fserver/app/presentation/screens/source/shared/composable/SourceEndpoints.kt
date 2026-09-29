package com.fserver.app.presentation.screens.source.shared.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.app.presentation.screens.source.shared.model.SourceEndpointUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

private val EndpointIconBox = 40.dp

/** The two ends of a source, with the way files move between them. */
@Composable
fun SourceEndpoints(
    modifier: Modifier = Modifier,
    origin: SourceEndpointUi,
    target: SourceEndpointUi,
    mode: SourceModeUi,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Endpoint(modifier = Modifier.weight(1f), endpoint = origin)
        Icon(
            modifier = Modifier
                .padding(top = DkSpacing.sm, start = DkSpacing.sm, end = DkSpacing.sm)
                .size(24.dp),
            imageVector = if (mode == SourceModeUi.Sync) {
                LinkDirectionUi.Mirror.icon
            } else {
                LinkDirectionUi.Outgoing.icon
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Endpoint(
            modifier = Modifier.weight(1f),
            endpoint = target,
            alignment = Alignment.End,
        )
    }
}

@Composable
private fun Endpoint(
    modifier: Modifier = Modifier,
    endpoint: SourceEndpointUi,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    val colors = MaterialTheme.colorScheme
    val textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start

    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(EndpointIconBox)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = endpoint.deviceKind.icon,
                contentDescription = null,
                tint = colors.onSurface,
            )
        }
        Text(
            modifier = Modifier.padding(top = DkSpacing.xs),
            text = endpoint.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        endpoint.detail?.let {
            Text(
                text = it.asString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SourceEndpointsPreview() {
    FServerTheme {
        SourceEndpoints(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            origin = SourceEndpointUi(name = "MacBook-Pro", deviceKind = DeviceKind.Laptop),
            target = SourceEndpointUi(name = "This phone", deviceKind = DeviceKind.Phone),
            mode = SourceModeUi.AutoUpload,
        )
    }
}
