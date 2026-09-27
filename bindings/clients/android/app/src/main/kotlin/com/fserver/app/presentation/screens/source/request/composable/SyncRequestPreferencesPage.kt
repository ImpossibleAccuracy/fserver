package com.fserver.app.presentation.screens.source.request.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.shared.preferences.composable.SourcePreferencesForm
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SyncRequestPreferencesPage(
    modifier: Modifier = Modifier,
    state: SyncRequestState,
    onIntent: (SyncRequestIntent) -> Unit,
) {
    val peerName = state.request?.deviceName.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = DkSpacing.lg, bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            text = stringResource(R.string.sync_request_preferences_body, peerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SourcePreferencesForm(
            state = state.preferences,
            onIntent = { onIntent(SyncRequestIntent.PreferencesChanged(it)) },
            peerName = peerName,
            sourceFiles = state.request?.files,
            sourceBytes = state.request?.bytes,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SyncRequestPreferencesPagePreview() {
    FServerTheme {
        SyncRequestPreferencesPage(state = SyncRequestState.Sample, onIntent = {})
    }
}
