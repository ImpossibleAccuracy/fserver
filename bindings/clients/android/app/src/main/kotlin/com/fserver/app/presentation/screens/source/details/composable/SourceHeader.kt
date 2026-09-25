package com.fserver.app.presentation.screens.source.details.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.ConditionUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private val EndpointIconBox = 40.dp

/** The two ends of the source, and the conditions it runs under. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceHeader(
    modifier: Modifier = Modifier,
    state: SourceDetailsState,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Endpoint(modifier = Modifier.weight(1f), endpoint = state.origin)
            Icon(
                modifier = Modifier
                    .padding(top = DkSpacing.sm, start = DkSpacing.sm, end = DkSpacing.sm)
                    .size(24.dp),
                imageVector = if (state.mode == SourceModeUi.Sync) {
                    Icons.Default.SwapHoriz
                } else {
                    Icons.AutoMirrored.Filled.ArrowForward
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Endpoint(
                modifier = Modifier.weight(1f),
                endpoint = state.target,
                alignment = Alignment.End,
            )
        }

        if (state.conditions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                state.conditions.forEach { condition ->
                    DkTag(text = condition.label(), style = DkTagStyle.Outline)
                }
            }
        }
    }
}

@Composable
private fun Endpoint(
    modifier: Modifier = Modifier,
    endpoint: SourceDetailsState.EndpointUi,
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
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConditionUi.label(): String = when (this) {
    is ConditionUi.Network -> stringResource(
        if (wifiOnly) R.string.source_details_condition_wifi_only
        else R.string.source_details_condition_any_network
    )

    ConditionUi.WhileCharging -> stringResource(R.string.source_details_condition_charging)
    is ConditionUi.IgnoreBefore -> stringResource(R.string.source_details_condition_since, dateLabel)
    ConditionUi.CopiesStay -> stringResource(R.string.source_details_condition_copies_stay)
    is ConditionUi.EvictOlderThan -> stringResource(R.string.source_details_condition_evict_older, days)
    is ConditionUi.EvictLargerThan -> stringResource(
        R.string.source_details_condition_evict_larger,
        FileSize(bytes).formatted(),
    )

    ConditionUi.KeepPinned -> stringResource(R.string.source_details_condition_keep_pinned)
    is ConditionUi.OnConflict -> stringResource(
        if (keepBoth) R.string.source_details_condition_conflict_keep_both
        else R.string.source_details_condition_conflict_newest
    )

    is ConditionUi.MaxFiles -> pluralStringResource(
        R.plurals.source_details_condition_max_files,
        count,
        count,
    )

    is ConditionUi.MaxSize -> stringResource(
        R.string.source_details_condition_max_size,
        FileSize(bytes).formatted(),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SourceHeaderPreview() {
    FServerTheme {
        SourceHeader(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            state = SourceDetailsState.SampleAutoUpload,
        )
    }
}
