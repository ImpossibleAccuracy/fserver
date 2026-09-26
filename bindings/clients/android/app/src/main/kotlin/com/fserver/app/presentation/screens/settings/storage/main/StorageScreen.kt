package com.fserver.app.presentation.screens.settings.storage.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.StorageUsage
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSkeletonBlock
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.settings.storage.main.composable.FreeUpCard
import com.fserver.app.presentation.composable.LinkDirectionIcons
import com.fserver.app.presentation.screens.settings.storage.main.composable.FreeUpSheet
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageIntent
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import org.koin.androidx.compose.koinViewModel

@Composable
fun StorageScreen(
    modifier: Modifier = Modifier,
    viewModel: StorageViewModel = koinViewModel(),
    navigateToSource: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    StorageScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToSource = navigateToSource,
        navigateUp = navigateUp,
    )
}

@Composable
private fun StorageScreenContent(
    modifier: Modifier = Modifier,
    state: StorageState,
    onIntent: (StorageIntent) -> Unit,
    navigateToSource: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    var freeUpOpen by rememberSaveable { mutableStateOf(false) }

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.storage_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item(key = "usage") {
                UsageHeader(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    state = state,
                )
            }

            if (state.isLoading) return@LazyColumn

            if (state.freeableBytes > 0) {
                item(key = "free") {
                    FreeUpCard(
                        modifier = Modifier.padding(
                            horizontal = DkSpacing.screenPadding,
                            vertical = DkSpacing.lg,
                        ),
                        freeableBytes = state.freeableBytes,
                        onFreeUp = { freeUpOpen = true },
                    )
                }
            }

            linksSection(
                links = state.links,
                totalBytes = state.linksBytes.takeIf { state.showsLinksTotal },
                onClick = navigateToSource,
            )
            if (state.appData.isNotEmpty()) {
                appDataSection(appData = state.appData, totalBytes = state.appDataBytes)
            }
        }
    }

    if (freeUpOpen && state.freeable.isNotEmpty()) {
        FreeUpSheet(
            usage = state.usage,
            items = state.freeable,
            initialSelection = state.defaultFreeSelection,
            onConfirm = { onIntent(StorageIntent.FreeUpConfirmed(it)) },
            onDismiss = { freeUpOpen = false },
        )
    }
}

@Composable
private fun UsageHeader(
    modifier: Modifier = Modifier,
    state: StorageState,
) {
    val usage = state.usage

    if (usage == null) {
        DkSkeletonBlock(
            modifier = modifier
                .fillMaxWidth()
                .height(88.dp),
        )
    } else {
        StorageUsage(modifier = modifier, storage = usage)
    }
}

private fun LazyListScope.linksSection(
    links: List<StorageState.LinkUi>,
    totalBytes: Long?,
    onClick: (String) -> Unit,
) {
    item(key = "links-label") {
        SectionHeader(text = stringResource(R.string.storage_links), bytes = totalBytes)
    }

    if (links.isEmpty()) {
        item(key = "links-empty") {
            EmptyCaption(text = stringResource(R.string.storage_links_empty))
        }
        return
    }

    itemsIndexed(links, key = { _, link -> "link-${link.id}" }) { index, link ->
        DkListRow(
            title = link.label,
            subtitle = stringResource(
                R.string.storage_link_subtitle,
                link.peer.name,
                pluralStringResource(R.plurals.storage_files, link.files, link.files),
            ),
            subtitleLeading = { LinkDirectionIcons(direction = link.direction, deviceKind = link.peer.kind) },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                ) {
                    DkMonoCaption(text = FileSize(link.bytes).formatted())
                    DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                }
            },
            onClick = { onClick(link.id) },
        )
        if (index != links.lastIndex) DkFadingDivider()
    }
}

private fun LazyListScope.appDataSection(
    appData: List<StorageState.AppDataUi>,
    totalBytes: Long,
) {
    item(key = "app-label") {
        SectionHeader(text = stringResource(R.string.storage_app_data), bytes = totalBytes)
    }

    itemsIndexed(appData, key = { _, data -> "app-${data.kind}" }) { index, data ->
        DkListRow(
            title = stringResource(data.kind.titleRes),
            subtitle = stringResource(data.kind.descriptionRes),
            trailing = { DkMonoCaption(text = FileSize(data.bytes).formatted()) },
        )
        if (index != appData.lastIndex) DkFadingDivider()
    }
}

@Composable
private fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String,
    bytes: Long?,
) {
    DkSectionLabel(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        text = text,
        trailing = bytes?.let { { DkMonoCaption(text = FileSize(it).formatted()) } },
    )
}

@Composable
private fun EmptyCaption(
    modifier: Modifier = Modifier,
    text: String,
) {
    DkCaption(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
        text = text,
    )
}

@get:StringRes
private val StorageState.AppDataKindUi.titleRes: Int
    get() = when (this) {
        StorageState.AppDataKindUi.Downloaded -> R.string.storage_app_downloaded
        StorageState.AppDataKindUi.Received -> R.string.storage_app_received
        StorageState.AppDataKindUi.Cache -> R.string.storage_app_cache
        StorageState.AppDataKindUi.Incomplete -> R.string.storage_app_incomplete
        StorageState.AppDataKindUi.Conflicts -> R.string.storage_app_conflicts
    }

@get:StringRes
private val StorageState.AppDataKindUi.descriptionRes: Int
    get() = when (this) {
        StorageState.AppDataKindUi.Downloaded -> R.string.storage_app_downloaded_desc
        StorageState.AppDataKindUi.Received -> R.string.storage_app_received_desc
        StorageState.AppDataKindUi.Cache -> R.string.storage_app_cache_desc
        StorageState.AppDataKindUi.Incomplete -> R.string.storage_app_incomplete_desc
        StorageState.AppDataKindUi.Conflicts -> R.string.storage_app_conflicts_desc
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun StorageScreenPreview() {
    FServerTheme {
        StorageScreenContent(
            state = StorageState.Sample,
            onIntent = {},
            navigateToSource = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StorageScreenEmptyPreview() {
    FServerTheme {
        StorageScreenContent(
            state = StorageState.SampleEmpty,
            onIntent = {},
            navigateToSource = {},
            navigateUp = {},
        )
    }
}
