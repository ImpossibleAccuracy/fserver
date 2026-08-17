package com.fserver.app.presentation.screens.discovery.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSpacing

/**
 * One way of reaching a device. The three are peers — none is drawn as the lesser path.
 *
 * Shared by the connect hub and the send target list, so the same three routes read the same
 * whether the user is connecting for the first time or picking who to send to.
 */
@Composable
fun ConnectRouteCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DkCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
            ) {
                DkCardTitle(text = title)
                DkCaption(text = description)
            }
            DkIcon(
                modifier = Modifier.padding(start = DkSpacing.sm),
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            )
        }
    }
}
