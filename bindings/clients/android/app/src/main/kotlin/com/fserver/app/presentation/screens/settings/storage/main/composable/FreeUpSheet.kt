package com.fserver.app.presentation.screens.settings.storage.main.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.StorageUsageUi
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.dkHatch
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState
import com.fserver.app.presentation.shared.browser.layouts.BrowserGalleryTile
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/**
 * What freeing space would drop, picked row by row, with the free space before and after.
 * App data starts ticked; a link's copies do not, since dropping them is a decision to make.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeUpSheet(
    modifier: Modifier = Modifier,
    usage: StorageUsageUi?,
    items: List<StorageState.FreeableUi>,
    initialSelection: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        FreeUpSheetContent(
            usage = usage,
            items = items,
            initialSelection = initialSelection,
            onConfirm = {
                onConfirm(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun FreeUpSheetContent(
    modifier: Modifier = Modifier,
    usage: StorageUsageUi?,
    items: List<StorageState.FreeableUi>,
    initialSelection: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(initialSelection) }
    val freedBytes = items.filter { it.key in selection }.sumOf { it.bytes }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.storage_free_title, FileSize(freedBytes).formatted()),
                style = MaterialTheme.typography.titleMedium,
            )
            DkCaption(text = stringResource(R.string.storage_free_body))

            if (usage != null) {
                FreeSpaceBars(
                    modifier = Modifier.padding(vertical = DkSpacing.sm),
                    usage = usage,
                    freedBytes = freedBytes,
                )
            }

            items.forEachIndexed { index, item ->
                FreeableRow(
                    item = item,
                    checked = item.key in selection,
                    onCheckedChange = { checked ->
                        selection = if (checked) selection + item.key else selection - item.key
                    },
                )
                if (index != items.lastIndex) DkFadingDivider()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DkSpacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            DkGhostButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
            DkPrimaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.storage_free_action),
                enabled = selection.isNotEmpty(),
                onClick = { onConfirm(selection) },
            )
        }
    }
}

@Composable
private fun FreeSpaceBars(
    modifier: Modifier = Modifier,
    usage: StorageUsageUi,
    freedBytes: Long,
) {
    val total = usage.totalBytes.coerceAtLeast(1).toFloat()
    val used = usage.usedBytes / total
    val freed = (freedBytes / total).coerceAtMost(used)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        FreeSpaceBar(
            label = stringResource(R.string.storage_free_now),
            used = used,
            freed = freed,
            freeBytes = usage.freeBytes,
        )
        FreeSpaceBar(
            label = stringResource(R.string.storage_free_after),
            used = used - freed,
            freed = 0f,
            freeBytes = usage.freeBytes + freedBytes,
        )
        DkCaption(
            modifier = Modifier.align(Alignment.End),
            text = stringResource(R.string.storage_free_free),
        )
    }
}

@Composable
private fun FreeSpaceBar(
    modifier: Modifier = Modifier,
    label: String,
    used: Float,
    freed: Float,
    freeBytes: Long,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkCaption(modifier = Modifier.width(48.dp), text = label)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(used)
                    .background(colors.primaryContainer)
                    .dkHatch(colors.primary, stripe = 2.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(used - freed)
                    .background(colors.outline),
            )
        }
        DkMonoCaption(text = FileSize(freeBytes).formatted())
    }
}

@Composable
private fun FreeableRow(
    modifier: Modifier = Modifier,
    item: StorageState.FreeableUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = DkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = item.title(),
                    style = MaterialTheme.typography.titleSmall,
                )
                DkMonoCaption(text = FileSize(item.bytes).formatted())
            }
            DkCaption(text = item.hint())

            if (item is StorageState.FreeableUi.LinkCopies && item.previews.isNotEmpty()) {
                PreviewStrip(
                    modifier = Modifier.padding(top = DkSpacing.xs),
                    previews = item.previews,
                    more = item.morePreviews,
                )
            }
        }
    }
}

@Composable
private fun PreviewStrip(
    modifier: Modifier = Modifier,
    previews: List<FileBrowserUi.File>,
    more: Int,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        previews.forEach { file ->
            BrowserGalleryTile(
                modifier = Modifier.weight(1f),
                file = file,
                selection = null,
                onFileClick = null,
            )
        }
        if (more > 0) {
            DkMediaTile(
                modifier = Modifier.weight(1f),
                extensionLabel = stringResource(R.string.storage_free_more, more),
            )
        }
    }
}

@Composable
private fun StorageState.FreeableUi.title(): String = when (this) {
    is StorageState.FreeableUi.AppData -> stringResource(
        when (data.kind) {
            StorageState.AppDataKindUi.Downloaded -> R.string.storage_app_downloaded
            StorageState.AppDataKindUi.Received -> R.string.storage_app_received
            StorageState.AppDataKindUi.Cache -> R.string.storage_app_cache
            StorageState.AppDataKindUi.Incomplete -> R.string.storage_app_incomplete
            StorageState.AppDataKindUi.Conflicts -> R.string.storage_app_conflicts
        }
    )

    is StorageState.FreeableUi.LinkCopies -> stringResource(R.string.storage_free_copies_title, label, deviceName)
}

@Composable
private fun StorageState.FreeableUi.hint(): String = when (this) {
    is StorageState.FreeableUi.AppData -> when (data.kind) {
        StorageState.AppDataKindUi.Downloaded -> stringResource(R.string.storage_free_downloaded_hint)
        StorageState.AppDataKindUi.Incomplete -> stringResource(R.string.storage_free_incomplete_hint)
        else -> stringResource(R.string.storage_free_cache_hint)
    }

    is StorageState.FreeableUi.LinkCopies -> pluralStringResource(R.plurals.storage_free_copies_hint, files, files)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FreeUpSheetPreview() {
    val state = StorageState.Sample

    FServerTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            FreeUpSheetContent(
                usage = state.usage,
                items = state.freeable,
                initialSelection = state.defaultFreeSelection,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
