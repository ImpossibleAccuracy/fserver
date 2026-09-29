package com.fserver.app.presentation.screens.files.composable

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkFilterChip
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.files.model.FilesState

/** Status and source filters. Picks are a draft until the sheet goes away, then applied at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesFilterSheet(
    sources: List<FilesState.SourceUi>,
    selectedSourceId: String?,
    filter: FilesState.FilterUi,
    onApply: (sourceId: String?, filter: FilesState.FilterUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftSourceId by rememberSaveable { mutableStateOf(selectedSourceId) }
    var draftFilter by rememberSaveable { mutableStateOf(filter) }

    ModalBottomSheet(
        onDismissRequest = {
            onApply(draftSourceId, draftFilter)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // Source rows are full-bleed and carry the gutter themselves; everything else takes it here.
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.xl),
        ) {
            Text(
                modifier = gutter,
                text = stringResource(R.string.files_filter_title),
                style = MaterialTheme.typography.titleMedium,
            )

            DkSectionLabel(modifier = gutter, text = stringResource(R.string.files_filter_status))
            FlowRow(
                modifier = gutter,
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

            if (sources.isNotEmpty()) {
                DkSectionLabel(modifier = gutter, text = stringResource(R.string.files_filter_source))

                SourceRow(
                    title = stringResource(R.string.files_filter_any_source),
                    icon = Icons.Default.AllInclusive,
                    selected = draftSourceId == null,
                    onClick = { draftSourceId = null },
                )
                sources.forEach { source ->
                    SourceRow(
                        title = source.label,
                        subtitle = source.peer.name,
                        icon = source.peer.kind.icon,
                        selected = source.id == draftSourceId,
                        onClick = { draftSourceId = source.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DkListRow(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { DkThumbnail(icon = icon) },
        trailing = { RadioButton(selected = selected, onClick = null) },
        contentPaddings = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ),
    )
}
