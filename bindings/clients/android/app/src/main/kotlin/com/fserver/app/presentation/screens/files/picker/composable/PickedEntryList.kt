package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState

@Composable
fun PickedEntryList(
    modifier: Modifier = Modifier,
    entries: List<FilesPickerState.PickedEntryUi>,
    onRemove: (FilesPickerState.PickedEntryUi) -> Unit,
) {
    Column(modifier = modifier) {
        entries.forEach { entry ->
            SwipeToRemoveRow(onRemove = { onRemove(entry) }) {
                PickedEntryRow(entry)
            }
            DkFadingDivider()
        }
    }
}

/**
 * Swipe in either direction to drop the row. The removal is reported once the swipe has
 * settled off-screen; the row then leaves the list by its key, so no reset is needed.
 */
@Composable
private fun SwipeToRemoveRow(
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { RemoveBackground(dismissState.dismissDirection) },
        content = { content() },
    )
}

@Composable
private fun RemoveBackground(direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    val backgroundColor by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.StartToEnd,
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer

            SwipeToDismissBoxValue.Settled -> Color.Transparent
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = DkSpacing.xl),
        contentAlignment = alignment,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.picker_remove_entry),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
