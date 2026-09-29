package com.fserver.app.presentation.composable

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkTopBar

/** The top bar while items are being picked: a close button in place of back, [title] as the count. */
@Composable
fun SelectionTopBar(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    DkTopBar(
        modifier = modifier,
        title = title,
        onBack = onClose,
        backIcon = Icons.Default.Close,
        backLabel = stringResource(R.string.action_close),
        actions = actions,
    )
}
