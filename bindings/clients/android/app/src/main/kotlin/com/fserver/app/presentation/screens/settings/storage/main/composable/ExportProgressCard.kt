package com.fserver.app.presentation.screens.settings.storage.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardKicker
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

@Composable
fun ExportProgressCard(
    modifier: Modifier = Modifier,
    export: StorageState.ExportUi,
) {
    DkCard(modifier = modifier.fillMaxWidth()) {
        DkCardKicker(text = stringResource(R.string.storage_export_running))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DkSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DkCaption(
                text = stringResource(R.string.storage_export_files, export.files, export.totalFiles),
            )
            DkMonoCaption(
                text = "${FileSize(export.writtenBytes).formatted()} / ${FileSize(export.totalBytes).formatted()}",
            )
        }
        DkProgressBar(progress = export.fraction)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ExportProgressCardPreview() {
    FServerTheme {
        ExportProgressCard(export = StorageState.SampleExporting.export!!)
    }
}
