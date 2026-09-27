package com.fserver.app.presentation.screens.source.request

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.composable.SyncRequestDetailsPage
import com.fserver.app.presentation.screens.source.request.composable.SyncRequestLocationPage
import com.fserver.app.presentation.screens.source.request.composable.SyncRequestPreferencesPage
import com.fserver.app.presentation.screens.source.request.composable.SyncRequestTopBar
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.request.model.SyncRequestUiEffect
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class Step { Details, Location, Preferences }

@Composable
fun SyncRequestScreen(
    modifier: Modifier = Modifier,
    key: Destination.Source.Request.Details,
    viewModel: SyncRequestViewModel = koinViewModel { parametersOf(key) },
    navigateToSource: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SyncRequestUiEffect.Declined -> navigateUp()
                is SyncRequestUiEffect.Accepted -> navigateToSource(effect.sourceId)
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(context::persistHostTree)?.fold(
            onSuccess = viewModel::onIntent,
            // A folder the user picked and the app then cannot write to is not a no-op: without
            // this the pick silently does nothing.
            onFailure = {
                viewModel.reporter.report(
                    error = it,
                    context = "Could not take a write grant on %s".format(uri)
                )
            },
        )
    }

    state?.let { state ->
        SyncRequestContent(
            modifier = modifier,
            state = state,
            onIntent = viewModel::onIntent,
            onPickFolder = { folderLauncher.launch(null) },
            navigateUp = navigateUp,
        )
    }
}

@Composable
private fun SyncRequestContent(
    modifier: Modifier = Modifier,
    state: SyncRequestState,
    onIntent: (SyncRequestIntent) -> Unit,
    onPickFolder: () -> Unit,
    navigateUp: () -> Unit,
    initialStep: Step = Step.Details,
) {
    val pager = rememberPagerState(initialPage = initialStep.ordinal) { Step.entries.size }
    val scope = rememberCoroutineScope()
    val step = Step.entries[pager.currentPage]

    val goTo: (Step) -> Unit = { scope.launch { pager.animateScrollToPage(it.ordinal) } }
    val back: () -> Unit = {
        if (step == Step.Details) navigateUp() else goTo(Step.entries[step.ordinal - 1])
    }

    BackHandler(enabled = step != Step.Details && !state.isGone, onBack = back)

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SyncRequestTopBar(
                title = stringResource(step.titleRes),
                subtitle = state.request?.label?.takeIf { step != Step.Details },
                step = step.ordinal + 1,
                onBack = back,
            )
        },
        bottomBar = {
            DkActionBar {
                if (state.isGone) {
                    DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_back),
                        onClick = navigateUp,
                    )
                    return@DkActionBar
                }

                when (step) {
                    Step.Details -> DetailsActions(
                        state = state,
                        onIntent = onIntent,
                        onNext = { goTo(Step.Location) },
                    )

                    Step.Location -> LocationActions(
                        state = state,
                        onNext = { goTo(Step.Preferences) },
                        onBack = back,
                    )

                    Step.Preferences -> PreferencesActions(
                        state = state,
                        onIntent = onIntent,
                        onBack = back,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (state.isGone) {
            SourceAccessFailure(
                modifier = Modifier.padding(innerPadding),
                title = stringResource(R.string.sync_request_gone_title),
                body = stringResource(R.string.sync_request_gone_body),
            )
            return@DkScaffold
        }

        val request = state.request ?: return@DkScaffold

        SyncRequestPager(
            modifier = Modifier.padding(innerPadding),
            pager = pager,
        ) { page ->
            when (page) {
                Step.Details -> SyncRequestDetailsPage(request = request)
                Step.Location -> SyncRequestLocationPage(
                    state = state,
                    onIntent = onIntent,
                    onPickFolder = onPickFolder,
                )

                Step.Preferences -> SyncRequestPreferencesPage(state = state, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun SyncRequestPager(
    modifier: Modifier = Modifier,
    pager: PagerState,
    content: @Composable (Step) -> Unit,
) {
    HorizontalPager(
        modifier = modifier.fillMaxSize(),
        state = pager,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
    ) { page ->
        content(Step.entries[page])
    }
}

@Composable
private fun ColumnScope.DetailsActions(
    state: SyncRequestState,
    onIntent: (SyncRequestIntent) -> Unit,
    onNext: () -> Unit,
) {
    DkCaption(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.sync_request_note),
        textAlign = TextAlign.Center,
    )
    DkPrimaryButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_continue),
        onClick = onNext,
        enabled = state.canAnswer,
    )
    DkGhostButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_decline),
        onClick = { onIntent(SyncRequestIntent.Declined) },
        enabled = state.canAnswer,
        danger = true,
    )
}

@Composable
private fun ColumnScope.LocationActions(
    state: SyncRequestState,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    if (state.disk != null) {
        DiskSpace(disk = state.disk, neededBytes = state.request?.bytes)
    }
    DkPrimaryButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_next),
        onClick = onNext,
        enabled = state.canAnswer,
    )
    DkGhostButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_back),
        onClick = onBack,
    )
}

@Composable
private fun ColumnScope.PreferencesActions(
    state: SyncRequestState,
    onIntent: (SyncRequestIntent) -> Unit,
    onBack: () -> Unit,
) {
    DkPrimaryButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_accept),
        onClick = { onIntent(SyncRequestIntent.Accepted) },
        enabled = state.canAnswer,
    )
    DkGhostButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.action_back),
        onClick = onBack,
        enabled = !state.isAnswering,
    )
}

@Composable
private fun DiskSpace(
    modifier: Modifier = Modifier,
    disk: SyncRequestState.DiskUi,
    neededBytes: Long?,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Row {
            DkCaption(
                modifier = Modifier.weight(1f),
                text = stringResource(
                    R.string.sync_request_location_disk_needed,
                    neededBytes?.let { FileSize(it).formatted() }
                        ?: stringResource(R.string.source_preferences_size_unknown),
                ),
            )
            DkCaption(
                text = stringResource(
                    R.string.sync_request_location_disk_free,
                    FileSize(disk.freeBytes).formatted(),
                ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val used = disk.usedFraction.coerceIn(0f, 1f)
            val needed = neededBytes?.let(disk::fractionOf) ?: 0f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((used + needed).coerceAtMost(1f))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(used)
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
    }
}

private val Step.titleRes: Int
    get() = when (this) {
        Step.Details -> R.string.sync_request_title
        Step.Location -> R.string.sync_request_location_title
        Step.Preferences -> R.string.sync_request_preferences_title
    }

private fun Context.persistHostTree(uri: Uri): Result<SyncRequestIntent.FolderPicked> {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    return runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        .map {
            val label = runCatching { DocumentsContract.getTreeDocumentId(uri) }
                .getOrNull()
                ?.substringAfter(':')
                ?.takeIf { segment -> segment.isNotEmpty() }
                ?: uri.lastPathSegment.orEmpty()

            SyncRequestIntent.FolderPicked(
                uri = uri.toString(),
                label = "/$label",
                hasFiles = hasChildren(uri),
            )
        }
}

private fun Context.hasChildren(tree: Uri): Boolean = runCatching {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        tree,
        DocumentsContract.getTreeDocumentId(tree),
    )
    contentResolver
        .query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
        ?.use { it.count > 0 }
        ?: false
}.getOrDefault(false)

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDetailsPreview() {
    FServerTheme {
        SyncRequestContent(
            state = SyncRequestState.Sample,
            onIntent = {},
            onPickFolder = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Location", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestLocationPreview() {
    FServerTheme {
        SyncRequestContent(
            state = SyncRequestState.Sample,
            onIntent = {},
            onPickFolder = {},
            navigateUp = {},
            initialStep = Step.Location,
        )
    }
}

@Preview(name = "Preferences", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestPreferencesPreview() {
    FServerTheme {
        SyncRequestContent(
            state = SyncRequestState.Sample,
            onIntent = {},
            onPickFolder = {},
            navigateUp = {},
            initialStep = Step.Preferences,
        )
    }
}

@Preview(name = "Already answered", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestGonePreview() {
    FServerTheme {
        SyncRequestContent(
            state = SyncRequestState(),
            onIntent = {},
            onPickFolder = {},
            navigateUp = {},
        )
    }
}
