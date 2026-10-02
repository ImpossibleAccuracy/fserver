package com.fserver.app.presentation.screens.source.details.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.binaryToDecimal
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.ConditionUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceEndpoints
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

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
        SourceEndpoints(origin = state.origin, target = state.target, mode = state.mode)

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
        FileSize(bytes).binaryToDecimal().formatted(),
    )

    is ConditionUi.OnConflict -> stringResource(
        if (ask) R.string.source_details_condition_conflict_ask
        else R.string.source_details_condition_conflict_newest
    )

    is ConditionUi.MaxFiles -> pluralStringResource(
        R.plurals.source_details_condition_max_files,
        count,
        count,
    )

    is ConditionUi.MaxSize -> stringResource(
        R.string.source_details_condition_max_size,
        FileSize(bytes).binaryToDecimal().formatted(),
    )

    ConditionUi.Encrypted -> stringResource(R.string.source_details_condition_encrypted)
    ConditionUi.NotEncryptable -> stringResource(R.string.source_details_condition_not_encryptable)
    is ConditionUi.Encrypting -> pluralStringResource(
        R.plurals.source_details_condition_encrypting,
        remaining,
        remaining,
    )

    is ConditionUi.Decrypting -> pluralStringResource(
        R.plurals.source_details_condition_decrypting,
        remaining,
        remaining,
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
