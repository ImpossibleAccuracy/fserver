package com.fserver.app.presentation.screens.request.done

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.request.done.model.SyncRequestDoneState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SyncRequestDoneScreen(
    modifier: Modifier = Modifier,
    key: Destination.SyncRequest.Done,
    viewModel: SyncRequestDoneViewModel = koinViewModel { parametersOf(key) },
    navigateToFiles: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SyncRequestDoneContent(
        modifier = modifier,
        state = state,
        navigateToFiles = navigateToFiles,
    )
}

@Composable
private fun SyncRequestDoneContent(
    modifier: Modifier = Modifier,
    state: SyncRequestDoneState,
    navigateToFiles: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_done_to_files),
                    onClick = navigateToFiles,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(top = DkSpacing.xxl, bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.sync_request_done_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.sync_request_done_body, state.deviceName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            DkCard {
                DkValueRow(
                    title = stringResource(R.string.sync_request_source),
                    value = state.label,
                )
                DkValueRow(
                    title = stringResource(R.string.sync_request_from),
                    value = state.deviceName,
                )
                state.modeRes?.let { mode ->
                    DkValueRow(
                        title = stringResource(R.string.sync_request_mode),
                        value = stringResource(mode),
                    )
                }
                DkValueRow(
                    title = stringResource(R.string.sync_request_done_saved_to),
                    value = state.locationLabel
                        ?: state.locationRes?.let { stringResource(it) }
                        ?: "",
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDonePreview() {
    FServerTheme {
        SyncRequestDoneContent(
            state = SyncRequestDoneState(
                label = "DCIM/Projects",
                deviceName = "MacBook-Pro",
                modeRes = R.string.mode_sync_title,
                locationRes = R.string.sync_request_location_internal_title,
            ),
            navigateToFiles = {},
        )
    }
}
