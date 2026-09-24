package com.fserver.app.presentation.screens.dashboard.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme
import kotlin.math.roundToInt

/** Every source as "what → where", with how it runs underneath. */
@Composable
fun LinksSection(
    modifier: Modifier = Modifier,
    links: List<DashboardState.LinkUi>,
    onLinkClick: (String) -> Unit,
) {
    Column(modifier = modifier) {
        links.forEachIndexed { index, link ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            LinkRow(link = link, onClick = { onLinkClick(link.id) })
        }
    }
}

@Composable
private fun LinkRow(
    link: DashboardState.LinkUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = link.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = link.directionIcon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = link.deviceKind.icon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = link.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val disabled = link.status == DashboardState.LinkStatusUi.Disabled
            Text(
                text = link.subtitle(),
                style = MaterialTheme.typography.labelSmall,
                color = if (disabled) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }
}

private val DashboardState.LinkUi.directionIcon
    get() = when {
        mode == SourceModeUi.Sync -> Icons.Default.SwapHoriz
        outgoing -> Icons.AutoMirrored.Filled.ArrowForward
        else -> Icons.AutoMirrored.Filled.ArrowBack
    }

/** "Auto-upload · synced 5 min. ago": the mode, then whatever is true of it right now. */
@Composable
private fun DashboardState.LinkUi.subtitle(): String {
    val state = when (status) {
        DashboardState.LinkStatusUi.Pending -> stringResource(R.string.dashboard_link_pending)

        DashboardState.LinkStatusUi.Syncing -> progress
            ?.let { stringResource(R.string.dashboard_link_syncing_progress, (it * 100).roundToInt()) }
            ?: stringResource(R.string.dashboard_link_syncing)

        DashboardState.LinkStatusUi.Disabled -> statusDetail

        DashboardState.LinkStatusUi.Active -> statusDetail
            ?.let { stringResource(R.string.dashboard_link_synced, it) }
            ?: stringResource(R.string.dashboard_link_never)
    }

    return listOfNotNull(stringResource(mode.titleRes), state).joinToString(" · ")
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LinksSectionPreview() {
    FServerTheme {
        LinksSection(
            links = DashboardState.Sample.links,
            onLinkClick = {},
        )
    }
}
