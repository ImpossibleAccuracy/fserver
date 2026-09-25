package com.fserver.app.presentation.screens.source.shared.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceProgressStep
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressState
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceProgressScreen(
    modifier: Modifier = Modifier,
    key: Destination.Source.Progress,
    viewModel: SourceProgressViewModel = koinViewModel { parametersOf(key) },
    navigateToDone: () -> Unit,
    closeFlow: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SourceProgressUiEffect.NavigateToDone -> navigateToDone()
            }
        }
    }

    SourceProgressContent(
        modifier = modifier,
        state = state,
        closeFlow = closeFlow,
    )
}

@Composable
private fun SourceProgressContent(
    modifier: Modifier = Modifier,
    state: SourceProgressState,
    closeFlow: () -> Unit,
) {
    BackHandler(onBack = closeFlow)

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DkTopBar(title = stringResource(R.string.source_progress_title)) },
        bottomBar = {
            DkActionBar {
                DkCaption(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_progress_background_note),
                    textAlign = TextAlign.Center,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_progress_close),
                    onClick = closeFlow,
                )
            }
        },
    ) { innerPadding ->
        val bodyModifier = Modifier.padding(innerPadding)

        when (state.phase) {
            SourceProgressState.Phase.Waiting -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(state.waitingTitleRes),
                body = stringResource(state.waitingBodyRes, state.peerName),
                progress = null,
                detail = state.sourceLabel,
            )

            SourceProgressState.Phase.Syncing -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(state.syncingTitleRes),
                body = stringResource(R.string.source_progress_syncing_body, state.peerName),
                progress = state.displayedProgress,
                detail = state.syncingDetail(),
            )

            SourceProgressState.Phase.Refused -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(R.string.source_progress_refused_title, state.peerName),
                body = state.reason.orEmpty(),
            )
        }
    }
}

private val SourceProgressState.waitingTitleRes: Int
    get() = when (role) {
        SourceRoleUi.Initiator -> R.string.source_progress_waiting_title
        SourceRoleUi.Follower -> R.string.source_progress_incoming_waiting_title
    }

private val SourceProgressState.waitingBodyRes: Int
    get() = when (role) {
        SourceRoleUi.Initiator -> R.string.source_progress_waiting_body
        SourceRoleUi.Follower -> R.string.source_progress_incoming_waiting_body
    }

private val SourceProgressState.syncingTitleRes: Int
    get() = when (role) {
        SourceRoleUi.Initiator -> R.string.source_progress_syncing_title
        SourceRoleUi.Follower -> R.string.source_progress_incoming_syncing_title
    }

@Composable
private fun SourceProgressState.syncingDetail(): String = when {
    showsPassDetail && isPlanned ->
        stringResource(R.string.source_progress_detail, actionsDone, actionsPlanned)

    showsPassDetail -> stringResource(R.string.source_progress_scanning_detail)

    filesTotal > 0 -> stringResource(R.string.source_progress_files_count, filesDone, filesTotal)

    else -> stringResource(R.string.source_progress_files_detail, filesDone)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceProgressWaitingPreview() {
    FServerTheme {
        SourceProgressContent(
            state = SourceProgressState(
                phase = SourceProgressState.Phase.Waiting,
                peerName = "HOME-NAS",
                sourceLabel = "/DCIM/Projects",
            ),
            closeFlow = {},
        )
    }
}

@Preview(name = "Syncing", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceProgressSyncingPreview() {
    FServerTheme {
        SourceProgressContent(
            state = SourceProgressState(
                phase = SourceProgressState.Phase.Syncing,
                peerName = "HOME-NAS",
                sourceLabel = "/DCIM/Projects",
                progress = 0.35f,
                actionsPlanned = 842,
                actionsDone = 294,
                isCounted = true,
                isPlanned = true,
                filesDone = 120,
                filesTotal = 310,
            ),
            closeFlow = {},
        )
    }
}

@Preview(name = "Receiving", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceProgressReceivingPreview() {
    FServerTheme {
        SourceProgressContent(
            state = SourceProgressState(
                role = SourceRoleUi.Follower,
                phase = SourceProgressState.Phase.Syncing,
                peerName = "Pixel 8",
                sourceLabel = "/DCIM/Projects",
                filesDone = 72,
                filesTotal = 80,
            ),
            closeFlow = {},
        )
    }
}

@Preview(name = "Refused", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceProgressRefusedPreview() {
    FServerTheme {
        SourceProgressContent(
            state = SourceProgressState(
                phase = SourceProgressState.Phase.Refused,
                peerName = "HOME-NAS",
                reason = "Rejected by user",
            ),
            closeFlow = {},
        )
    }
}
