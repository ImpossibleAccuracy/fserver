package com.fserver.app.presentation.screens.dashboard.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.presentation.composable.LinkDirectionIcons
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
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
                LinkDirectionIcons(direction = link.direction, deviceKind = link.deviceKind)
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = link.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            link.subtitle()?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (link.isFailing) colors.error else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }
}

private val DashboardState.LinkUi.isFailing: Boolean
    get() = status != DashboardState.LinkStatusUi.Syncing &&
            (status == DashboardState.LinkStatusUi.Disabled || error != null)

/** "syncing · 40% · 12 of 30 files", or the one thing wrong with the link right now. */
@Composable
private fun DashboardState.LinkUi.subtitle(): String? = when {
    status == DashboardState.LinkStatusUi.Syncing -> listOfNotNull(
        stringResource(R.string.dashboard_link_syncing),
        progress?.let { "${(it * 100).roundToInt()}%" },
        filesTotal.takeIf { it > 0 }
            ?.let { stringResource(R.string.dashboard_link_files, filesDone, it) },
    ).joinToString(" · ")

    status == DashboardState.LinkStatusUi.Disabled -> statusDetail

    error != null -> error.asString()

    status == DashboardState.LinkStatusUi.Pending -> stringResource(R.string.dashboard_link_pending)

    else -> statusDetail?.let { stringResource(R.string.dashboard_link_synced, it) }
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
