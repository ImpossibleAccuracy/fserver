package com.fserver.app.presentation.screens.source.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.source.list.model.SyncRequestListIntent
import com.fserver.app.presentation.screens.source.list.model.SyncRequestListState
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SyncRequestListScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncRequestListViewModel = koinViewModel(),
    navigateToDetails: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SyncRequestListContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToDetails = navigateToDetails,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SyncRequestListContent(
    modifier: Modifier = Modifier,
    state: SyncRequestListState,
    onIntent: (SyncRequestListIntent) -> Unit,
    navigateToDetails: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.sync_requests_title),
                onBack = navigateUp,
                actions = {
                    TextButton(
                        onClick = { onIntent(SyncRequestListIntent.DeclinedAll) },
                        enabled = state.canAnswer,
                    ) {
                        Text(
                            text = stringResource(R.string.sync_requests_decline_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = DkSpacing.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                DkCaption(
                    text = stringResource(R.string.sync_requests_empty),
                    textAlign = TextAlign.Center,
                )
            }
            return@DkScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            item(key = "summary") {
                DkCaption(
                    modifier = Modifier.padding(bottom = DkSpacing.xs),
                    text = stringResource(R.string.sync_requests_count, state.requests.size),
                )
            }

            items(state.requests, key = { it.sourceId }) { request ->
                SyncRequestCard(
                    request = request,
                    enabled = state.canAnswer,
                    onAccept = { navigateToDetails(request.sourceId) },
                    onDecline = { onIntent(SyncRequestListIntent.Declined(request.sourceId)) },
                )
            }

            item(key = "footnote") {
                DkCaption(
                    modifier = Modifier.padding(vertical = DkSpacing.md),
                    text = stringResource(R.string.sync_requests_footnote),
                )
            }
        }
    }
}

@Composable
private fun SyncRequestCard(
    modifier: Modifier = Modifier,
    request: SyncRequestUi,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    DkCard(modifier = modifier, outlined = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.md)) {
            Icon(
                modifier = Modifier
                    .padding(top = DkSpacing.xxs)
                    .size(16.dp),
                imageVector = Icons.Default.SyncAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.sync_requests_card_title,
                        request.deviceName,
                        stringResource(request.mode.titleRes),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DkCaption(
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                    text = stringResource(R.string.sync_requests_card_body, request.label),
                )
            }
        }

        DkMonoCaption(text = stringResource(R.string.sync_requests_card_trusted))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkPrimaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.action_accept),
                onClick = onAccept,
                enabled = enabled,
            )
            DkGhostButton(
                text = stringResource(R.string.action_decline),
                onClick = onDecline,
                enabled = enabled,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestListPreview() {
    FServerTheme {
        SyncRequestListContent(
            state = SyncRequestListState(requests = SyncRequestListState.SampleRequests),
            onIntent = {},
            navigateToDetails = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestListEmptyPreview() {
    FServerTheme {
        SyncRequestListContent(
            state = SyncRequestListState(),
            onIntent = {},
            navigateToDetails = {},
            navigateUp = {},
        )
    }
}
