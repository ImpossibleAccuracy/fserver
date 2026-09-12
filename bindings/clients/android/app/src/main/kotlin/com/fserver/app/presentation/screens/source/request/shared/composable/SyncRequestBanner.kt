package com.fserver.app.presentation.screens.source.request.shared.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme

/**
 * The one place a parked ask surfaces: a row over the file list.
 *
 * It interrupts nothing — the peer's request waits in the engine until it is answered, so a card
 * the user opens when they are ready is the whole entry point.
 *
 * [waiting] is how many asks are parked in total; anything above the one shown is named rather
 * than stacked, so the list keeps one banner however many arrive.
 *
 * With [onDismiss] the card spells its two ways out as buttons instead of a chevron — what the
 * content feed needs, where the banner sits above the user's own files rather than in a list of
 * things asking for a decision.
 */
@Composable
fun SyncRequestBanner(
    modifier: Modifier = Modifier,
    request: SyncRequestUi,
    waiting: Int,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    DkCard(
        modifier = modifier,
        outlined = true,
        onClick = onClick.takeIf { onDismiss == null },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DkThumbnail(icon = Icons.Default.SyncAlt)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = DkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
            ) {
                DkCardTitle(text = stringResource(R.string.sync_request_banner_title))
                DkCaption(
                    text = stringResource(
                        R.string.sync_request_banner_body,
                        request.deviceName,
                        request.label,
                    )
                )
                if (waiting > 1) {
                    DkCaption(
                        text = stringResource(
                            R.string.sync_request_banner_more,
                            waiting - 1,
                        )
                    )
                }
            }
            if (onDismiss == null) {
                DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        }

        if (onDismiss != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                DkSecondaryButton(
                    text = stringResource(R.string.sync_request_action_show),
                    onClick = onClick,
                )
                DkGhostButton(
                    text = stringResource(R.string.action_later),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SyncRequestBannerPreview() {
    FServerTheme {
        SyncRequestBanner(
            request = SyncRequestUi(
                sourceId = "3f2a",
                deviceName = "MacBook-Pro",
                label = "DCIM/Projects",
                mode = SourceModeUi.Sync,
            ),
            waiting = 2,
            onClick = {},
        )
    }
}

@Preview(name = "With actions", showBackground = true, widthDp = 360)
@Composable
private fun SyncRequestBannerActionsPreview() {
    FServerTheme {
        SyncRequestBanner(
            request = SyncRequestUi(
                sourceId = "3f2a",
                deviceName = "MacBook-Pro",
                label = "DCIM/Projects",
                mode = SourceModeUi.Sync,
            ),
            waiting = 3,
            onClick = {},
            onDismiss = {},
        )
    }
}
