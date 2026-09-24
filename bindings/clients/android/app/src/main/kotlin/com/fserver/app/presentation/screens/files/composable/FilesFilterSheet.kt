package com.fserver.app.presentation.screens.files.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkFilterChip
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.files.model.FilesState

/** Device and status filters. Picks are a draft until the sheet goes away, then applied at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesFilterSheet(
    devices: List<FilesState.DeviceUi>,
    selectedDeviceId: String?,
    filter: FilesState.FilterUi,
    onApply: (deviceId: String?, filter: FilesState.FilterUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftDeviceId by rememberSaveable { mutableStateOf(selectedDeviceId) }
    var draftFilter by rememberSaveable { mutableStateOf(filter) }

    ModalBottomSheet(
        onDismissRequest = {
            onApply(draftDeviceId, draftFilter)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.files_filter_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (devices.isNotEmpty()) {
                DkSectionLabel(text = stringResource(R.string.files_filter_device))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                ) {
                    DkFilterChip(
                        text = stringResource(R.string.files_filter_any_device),
                        selected = draftDeviceId == null,
                        onClick = { draftDeviceId = null },
                    )
                    devices.forEach { device ->
                        DkFilterChip(
                            text = device.name,
                            selected = device.id == draftDeviceId,
                            onClick = { draftDeviceId = device.id },
                            icon = device.kind.icon,
                        )
                    }
                }
            }

            DkSectionLabel(text = stringResource(R.string.files_filter_status))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkFilterChip(
                    text = stringResource(R.string.files_filter_all),
                    selected = draftFilter == FilesState.FilterUi.All,
                    onClick = { draftFilter = FilesState.FilterUi.All },
                    icon = Icons.AutoMirrored.Filled.ViewList,
                )
                DkFilterChip(
                    text = stringResource(R.string.files_filter_local),
                    selected = draftFilter == FilesState.FilterUi.Local,
                    onClick = { draftFilter = FilesState.FilterUi.Local },
                    icon = Icons.Default.Smartphone,
                )
                DkFilterChip(
                    text = stringResource(R.string.files_filter_cloud),
                    selected = draftFilter == FilesState.FilterUi.Cloud,
                    onClick = { draftFilter = FilesState.FilterUi.Cloud },
                    icon = Icons.Default.Cloud,
                )
            }
        }
    }
}
