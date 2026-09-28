package com.fserver.app.presentation.screens.source.shared.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail

private const val UnavailableAlpha = 0.5f

/**
 * One source on screen 0. Every branch reads the same — the copy carries the warning, not the box.
 * An [unavailable] one stays tappable, so it can still say why.
 */
@Composable
fun SourceOptionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    unavailable: Boolean = false,
) {
    DkCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.alpha(if (unavailable) UnavailableAlpha else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DkThumbnail(icon = icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = DkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
            ) {
                DkCardTitle(text = title)
                DkCaption(text = description)
            }
            DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
        }
    }
}

/**
 * A single-choice row: mode, eviction rule. The row is the touch target, so the
 * label never has to be hit exactly.
 *
 * The radio sits on the title's own line rather than beside the whole block, which is what keeps
 * a one-line choice and a four-line one reading as the same control. Everything below the title
 * hangs off the same gutter.
 *
 * [warning] marks a choice that lets something be destroyed; [recommended] marks the default;
 * [navigates] marks one that opens another screen (a system picker) before it is selected.
 */
@Composable
fun SourceChoiceRow(
    modifier: Modifier = Modifier,
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    description: String? = null,
    recommended: Boolean = false,
    warning: Boolean = false,
    navigates: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    DkCard(modifier = modifier, onClick = onSelect, outlined = !selected) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (description != null) {
                    DkCaption(text = description)
                }
            }

            if (recommended) {
                DkTag(
                    text = stringResource(R.string.source_mode_recommended),
                    style = DkTagStyle.Accent,
                )
            }
            if (warning) {
                DkIcon(icon = Icons.Default.PriorityHigh)
            }
            if (navigates) {
                DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        }

        content?.invoke()
    }
}
