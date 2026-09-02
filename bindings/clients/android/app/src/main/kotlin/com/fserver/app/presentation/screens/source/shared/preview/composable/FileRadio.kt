package com.fserver.app.presentation.screens.source.shared.preview.composable

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi

@Composable
fun FileRadio(
    modifier: Modifier = Modifier,
    file: SourcePreviewUi.File,
    selection: SourcePreviewSelection?,
) {
    val onSelect = selection?.onSelectFile ?: return

    RadioButton(
        modifier = modifier,
        selected = file == selection.selected,
        onClick = { onSelect(file) },
    )
}
