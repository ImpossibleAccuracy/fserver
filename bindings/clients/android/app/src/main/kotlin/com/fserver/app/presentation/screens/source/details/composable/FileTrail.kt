package com.fserver.app.presentation.screens.source.details.composable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageKindUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import java.text.NumberFormat

private val NodeSize = 34.dp
private val RailWidth = 1.dp
private const val BreathScale = 0.1f
private const val BreathFade = 0.4f

/**
 * Where the source's files are, as stops along the way they travel. Nodes are joined by a rail,
 * and a stop with files waiting on it is drawn in the accent.
 */
@Composable
fun FileTrail(
    modifier: Modifier = Modifier,
    state: SourceDetailsState,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        state.stages.forEachIndexed { index, stage ->
            StageRow(
                stage = stage,
                state = state,
                first = index == 0,
                last = index == state.stages.lastIndex,
            )
        }
    }
}

@Composable
private fun StageRow(
    modifier: Modifier = Modifier,
    stage: StageUi,
    state: SourceDetailsState,
    first: Boolean,
    last: Boolean,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Rail(modifier = Modifier.weight(1f), visible = !first)
            Box(
                modifier = Modifier
                    .size(NodeSize)
                    .breathing(enabled = state.isSyncing && stage.isWaiting)
                    .background(colors.surface, CircleShape)
                    .border(
                        width = 1.dp,
                        color = if (stage.isWaiting) colors.primary else colors.outlineVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = stage.icon(state),
                    contentDescription = null,
                    tint = when {
                        stage.kind == StageKindUi.Matched -> colors.primary
                        stage.isWaiting -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                )
            }
            Rail(modifier = Modifier.weight(1f), visible = !last)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = DkSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stage.title(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                stage.subtitle(state)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormat.getIntegerInstance().format(stage.count),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (stage.kind == StageKindUi.Matched) colors.primary else colors.onSurface,
                )
                Text(
                    text = stringResource(stage.kind.badgeRes).uppercase(),
                    style = DkType.mono,
                    color = if (stage.kind == StageKindUi.Matched) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Slow pulse on a stop files are moving through right now. */
@Composable
private fun Modifier.breathing(enabled: Boolean): Modifier {
    if (!enabled) return this

    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathing-phase",
    )

    return graphicsLayer {
        val scale = 1f + BreathScale * phase
        scaleX = scale
        scaleY = scale
        alpha = 1f - BreathFade * phase
    }
}

@Composable
private fun Rail(modifier: Modifier = Modifier, visible: Boolean) {
    if (visible) {
        Box(
            modifier = modifier
                .width(RailWidth)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    } else {
        Spacer(modifier = modifier)
    }
}

private fun StageUi.icon(state: SourceDetailsState): ImageVector = when (kind) {
    StageKindUi.Here -> Icons.Default.Smartphone
    StageKindUi.Outgoing -> Icons.Default.ArrowDownward
    StageKindUi.Matched -> Icons.Default.Check
    StageKindUi.Incoming -> Icons.Default.ArrowUpward
    StageKindUi.Peer -> state.peer.kind.icon
    StageKindUi.Evicted -> Icons.Default.DeleteOutline
}

@Composable
private fun StageUi.title(state: SourceDetailsState): String {
    val sync = state.mode == SourceModeUi.Sync

    return when (kind) {
        StageKindUi.Here -> stringResource(R.string.source_details_stage_here)
        StageKindUi.Outgoing -> if (sync) {
            stringResource(R.string.source_details_stage_outgoing_to, state.peer.name)
        } else {
            stringResource(R.string.source_details_stage_outgoing)
        }

        StageKindUi.Matched -> stringResource(R.string.source_details_stage_matched)
        StageKindUi.Incoming -> stringResource(R.string.source_details_stage_incoming_from, state.peer.name)
        StageKindUi.Peer -> stringResource(R.string.source_details_stage_peer, state.peer.name)
        StageKindUi.Evicted -> stringResource(R.string.source_details_stage_evicted)
    }
}

@Composable
private fun StageUi.subtitle(state: SourceDetailsState): String? {
    val sync = state.mode == SourceModeUi.Sync
    val reason = when (kind) {
        StageKindUi.Outgoing -> when {
            sync -> stringResource(R.string.source_details_stage_changed_here)
            state.sendNow != null -> stringResource(R.string.source_details_stage_no_wifi)
            else -> null
        }

        StageKindUi.Matched -> stringResource(R.string.source_details_stage_same_both)
        StageKindUi.Incoming -> stringResource(R.string.source_details_stage_changed_on, state.peer.name)
        StageKindUi.Evicted -> stringResource(
            R.string.source_details_stage_only_on,
            state.peer.name
        )

        else -> null
    }
    val showSize = !sync || kind == StageKindUi.Here || kind == StageKindUi.Peer
    val size = bytes?.takeIf { it > 0 && showSize }?.let { FileSize(it).formatted() }

    return listOfNotNull(detail?.asString(), reason, size).joinToString(" · ").ifEmpty { null }
}

private val StageKindUi.badgeRes: Int
    get() = when (this) {
        StageKindUi.Here -> R.string.source_details_badge_local
        StageKindUi.Outgoing,
        StageKindUi.Incoming -> R.string.source_details_badge_waiting
        StageKindUi.Matched -> R.string.source_details_badge_matched
        StageKindUi.Peer -> R.string.source_details_badge_remote
        StageKindUi.Evicted -> R.string.source_details_badge_freed
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FileTrailPreview() {
    FServerTheme {
        FileTrail(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            state = SourceDetailsState.SampleSync,
        )
    }
}
