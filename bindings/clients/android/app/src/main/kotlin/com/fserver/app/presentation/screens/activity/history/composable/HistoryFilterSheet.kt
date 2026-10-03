package com.fserver.app.presentation.screens.activity.history.composable

import com.fserver.app.presentation.composable.model.icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.activity.history.model.ActivityHistoryState
import com.fserver.app.presentation.shared.journal.model.JournalGroupUi
import com.fserver.app.presentation.shared.journal.model.icon
import com.fserver.app.presentation.shared.journal.model.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterSheet(
    groups: Set<JournalGroupUi>,
    sources: List<ActivityHistoryState.SourceUi>,
    sourceIds: Set<String>,
    devices: List<ActivityHistoryState.DeviceUi>,
    deviceIds: Set<String>,
    onApply: (groups: Set<JournalGroupUi>, sourceIds: Set<String>, deviceIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftGroups by remember { mutableStateOf(groups) }
    var draftSources by remember { mutableStateOf(sourceIds) }
    var draftDevices by remember { mutableStateOf(deviceIds) }

    ModalBottomSheet(
        onDismissRequest = {
            onApply(draftGroups, draftSources, draftDevices)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.xl),
        ) {
            Text(
                modifier = gutter,
                text = stringResource(R.string.journal_filter_title),
                style = MaterialTheme.typography.titleMedium,
            )

            DkSectionLabel(modifier = gutter, text = stringResource(R.string.journal_filter_kind))
            CheckRow(
                title = stringResource(R.string.journal_filter_all),
                icon = Icons.Default.AllInclusive,
                checked = draftGroups.isEmpty(),
                onClick = { draftGroups = emptySet() },
            )
            JournalGroupUi.entries.forEach { group ->
                CheckRow(
                    title = stringResource(group.labelRes),
                    icon = group.icon,
                    checked = group in draftGroups,
                    onClick = { draftGroups = draftGroups.toggled(group) },
                )
            }

            if (sources.isNotEmpty()) {
                DkSectionLabel(modifier = gutter, text = stringResource(R.string.journal_filter_source))
                CheckRow(
                    title = stringResource(R.string.journal_filter_all_sources),
                    icon = Icons.Default.AllInclusive,
                    checked = draftSources.isEmpty(),
                    onClick = { draftSources = emptySet() },
                )
                sources.forEach { source ->
                    CheckRow(
                        title = source.label,
                        icon = Icons.Default.FolderOpen,
                        checked = source.id in draftSources,
                        onClick = { draftSources = draftSources.toggled(source.id) },
                    )
                }
            }

            if (devices.isNotEmpty()) {
                DkSectionLabel(modifier = gutter, text = stringResource(R.string.journal_filter_device))
                CheckRow(
                    title = stringResource(R.string.journal_filter_all_devices),
                    icon = Icons.Default.AllInclusive,
                    checked = draftDevices.isEmpty(),
                    onClick = { draftDevices = emptySet() },
                )
                devices.forEach { device ->
                    CheckRow(
                        title = device.name,
                        icon = device.kind.icon,
                        checked = device.id in draftDevices,
                        onClick = { draftDevices = draftDevices.toggled(device.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DkListRow(
        modifier = modifier,
        title = title,
        onClick = onClick,
        leading = { DkThumbnail(icon = icon) },
        trailing = { Checkbox(checked = checked, onCheckedChange = null) },
        contentPaddings = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ),
    )
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
