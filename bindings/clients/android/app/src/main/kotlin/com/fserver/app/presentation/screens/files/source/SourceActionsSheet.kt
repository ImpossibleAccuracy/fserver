package com.fserver.app.presentation.screens.files.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTextField
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.screens.files.source.model.SourceActionsIntent
import com.fserver.app.presentation.screens.files.source.model.SourceActionsState
import com.fserver.app.presentation.screens.files.source.model.SourceActionsUiEffect
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourceActionsSheet(
    modifier: Modifier = Modifier,
    viewModel: SourceActionsViewModel,
    dismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SourceActionsUiEffect.Dismiss -> dismiss()
            }
        }
    }

    SourceActionsSheetContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        dismiss = dismiss,
    )
}

@Composable
private fun SourceActionsSheetContent(
    modifier: Modifier = Modifier,
    state: SourceActionsState,
    onIntent: (SourceActionsIntent) -> Unit,
    dismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Text(
            modifier = Modifier.padding(vertical = DkSpacing.sm),
            text = stringResource(R.string.files_source_actions_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        DkTextField(
            label = stringResource(R.string.files_source_name_label),
            value = state.name,
            onValueChange = { onIntent(SourceActionsIntent.NameChanged(it)) },
        )

        DkPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.action_save),
            onClick = { onIntent(SourceActionsIntent.SaveClicked) },
            enabled = state.canSave,
        )

        state.mode?.let { mode ->
            DkValueRow(
                title = stringResource(R.string.files_source_mode_label),
                value = stringResource(mode.titleRes),
            )
            DkCaption(text = stringResource(R.string.files_source_mode_note))
        }

        if (state.confirmingRemoval) {
            DkCaption(text = stringResource(R.string.files_source_remove_note))
            DkSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.files_source_remove_confirm),
                onClick = { onIntent(SourceActionsIntent.RemoveConfirmed) },
                enabled = !state.isBusy,
            )
            DkGhostButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_cancel),
                onClick = { onIntent(SourceActionsIntent.RemoveCancelled) },
            )
        } else {
            DkGhostButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.files_source_remove),
                onClick = { onIntent(SourceActionsIntent.RemoveClicked) },
                enabled = !state.isBusy && !state.isMissing,
            )
            DkGhostButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_cancel),
                onClick = dismiss,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SourceActionsSheetPreview() {
    FServerTheme {
        SourceActionsSheetContent(
            state = SourceActionsState.Sample,
            onIntent = {},
            dismiss = {},
        )
    }
}

@Preview(name = "Removing", showBackground = true, widthDp = 360)
@Composable
private fun SourceActionsSheetRemovingPreview() {
    FServerTheme {
        SourceActionsSheetContent(
            state = SourceActionsState.Sample.copy(confirmingRemoval = true),
            onIntent = {},
            dismiss = {},
        )
    }
}
