package com.fserver.app.presentation.shared.browser.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

/** The tick a [selection] puts on a file. Slides in when one starts and out when it ends. */
@Composable
fun FileCheckbox(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
) {
    EntryCheckbox(
        modifier = modifier,
        visible = selection != null,
        checked = selection?.isSelected(file) == true,
        onCheckedChange = { selection?.onToggle(file) },
    )
}

/** The tick itself, for any entry: slides in while [visible]. */
@Composable
internal fun EntryCheckbox(
    modifier: Modifier = Modifier,
    visible: Boolean,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        Checkbox(
            modifier = Modifier.size(24.dp),
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
