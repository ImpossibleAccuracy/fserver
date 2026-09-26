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
    AnimatedVisibility(
        modifier = modifier,
        visible = selection != null,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        Checkbox(
            modifier = Modifier.size(24.dp),
            checked = selection?.isSelected(file) == true,
            onCheckedChange = { selection?.onToggle(file) },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
