package com.fserver.app.presentation.shared.browser

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

@Composable
fun FileRadio(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
) {
    val onSelect = selection?.onSelectFile ?: return

    RadioButton(
        modifier = modifier,
        selected = file == selection.selected,
        onClick = { onSelect(file) },
    )
}
