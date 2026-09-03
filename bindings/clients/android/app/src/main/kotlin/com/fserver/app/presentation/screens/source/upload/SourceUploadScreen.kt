package com.fserver.app.presentation.screens.source.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceProgressStep
import com.fserver.app.presentation.screens.source.upload.model.SourceUploadState
import com.fserver.app.presentation.screens.source.upload.model.SourceUploadUiEffect
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourceUploadScreen(
    handler: SourceUploadHandler,
    navigateToDone: () -> Unit,
    closeFlow: () -> Unit,
) {
    val state = handler.state.collectAsStateWithLifecycle().value ?: return

    LaunchedEffect(handler) {
        handler.effects.collect { effect ->
            when (effect) {
                SourceUploadUiEffect.NavigateToDone -> navigateToDone()
            }
        }
    }

    SourceUploadScreenContent(
        state = state,
        closeFlow = closeFlow,
    )
}

@Composable
private fun SourceUploadScreenContent(
    state: SourceUploadState,
    closeFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The source is registered by the time this screen opens, so there is nothing above it left
    // to answer again — back leaves the flow rather than walking it backwards.
    BackHandler(onBack = closeFlow)

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DkTopBar(title = stringResource(R.string.source_upload_title)) },
        bottomBar = {
            DkActionBar {
                DkCaption(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_upload_background_note),
                    textAlign = TextAlign.Center,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.source_upload_close),
                    onClick = closeFlow,
                )
            }
        },
    ) { innerPadding ->
        val bodyModifier = Modifier.padding(innerPadding)

        when (state.phase) {
            SourceUploadState.Phase.WaitingForPeer -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(R.string.source_upload_waiting_title),
                body = stringResource(R.string.source_upload_waiting_body, state.targetName),
                progress = null,
                detail = state.sourceLabel,
            )

            SourceUploadState.Phase.Syncing -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(R.string.source_upload_syncing_title),
                body = stringResource(R.string.source_upload_syncing_body, state.targetName),
                progress = state.progress,
                detail = state.progressDetail,
            )

            SourceUploadState.Phase.Refused -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(R.string.source_upload_refused_title, state.targetName),
                body = state.reason.orEmpty(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceUploadWaitingPreview() {
    FServerTheme {
        SourceUploadScreenContent(
            state = SourceUploadState(
                phase = SourceUploadState.Phase.WaitingForPeer,
                targetName = "HOME-NAS",
                sourceLabel = "/DCIM/Projects",
                files = 842,
            ),
            closeFlow = {},
        )
    }
}

@Preview(name = "Syncing", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceUploadSyncingPreview() {
    FServerTheme {
        SourceUploadScreenContent(
            state = SourceUploadState(
                phase = SourceUploadState.Phase.Syncing,
                targetName = "HOME-NAS",
                sourceLabel = "/DCIM/Projects",
                files = 842,
                progress = 0.35f,
                progressDetail = "294 of 842",
            ),
            closeFlow = {},
        )
    }
}

@Preview(name = "Refused", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceUploadRefusedPreview() {
    FServerTheme {
        SourceUploadScreenContent(
            state = SourceUploadState(
                phase = SourceUploadState.Phase.Refused,
                targetName = "HOME-NAS",
                reason = "Rejected by user",
            ),
            closeFlow = {},
        )
    }
}
