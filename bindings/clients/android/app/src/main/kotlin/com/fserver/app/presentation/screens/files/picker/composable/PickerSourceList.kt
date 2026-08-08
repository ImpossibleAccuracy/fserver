package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.PickerSource

/**
 * The entry point of the picker: which door to open. Each source asks for its own access and
 * then takes over the screen with the browsing shape that fits it.
 */
@Composable
fun PickerSourceList(
    sources: List<PickerSource>,
    onSourceSelected: (PickerSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        sources.forEach { source ->
            DkListRow(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSourceSelected(source) },
                title = stringResource(
                    when (source) {
                        PickerSource.StorageAccessFramework -> R.string.picker_source_saf_title
                        PickerSource.MediaStore -> R.string.picker_source_media_title
                        PickerSource.FullAccess -> R.string.picker_source_full_title
                    }
                ),
                subtitle = stringResource(
                    when (source) {
                        PickerSource.StorageAccessFramework -> R.string.picker_source_saf_subtitle
                        PickerSource.MediaStore -> R.string.picker_source_media_subtitle
                        PickerSource.FullAccess -> R.string.picker_source_full_subtitle
                    }
                ),
                subtitleStyle = DkType.mono,
                leading = {
                    DkThumbnail(
                        icon = when (source) {
                            PickerSource.StorageAccessFramework -> Icons.Default.AttachFile
                            PickerSource.MediaStore -> Icons.Default.Image
                            PickerSource.FullAccess -> Icons.Default.FileCopy
                        },
                    )
                },
                trailing = {
                    DkIcon(icon = Icons.Default.ChevronRight)
                },
            )
        }
    }
}
