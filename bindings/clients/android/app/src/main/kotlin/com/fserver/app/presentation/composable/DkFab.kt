package com.fserver.app.presentation.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.fserver.app.presentation.designkit.DkPillButton

private val LocalFabHostState = compositionLocalOf<DkFabHostState?> {
    null
}

@Composable
fun createFabHostState(
    fabHostState: DkFabHostState,
) = LocalFabHostState provides fabHostState

/**
 * This class is needed to let the global scaffold reserve space for the screen's own FAB,
 * so that the global snackbar is offset correctly by Material3's built-in FAB-avoidance logic.
 */
@Stable
class DkFabHostState {
    var size by mutableStateOf(DpSize.Zero)
        private set

    fun setup(size: DpSize) {
        this.size = size
    }
}


@Composable
fun DkFab(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String? = null,
    onClick: () -> Unit,
    visible: Boolean = true,
) {
    AnimatedFab(visible = visible) {
        Box(modifier = modifier.reportFabSize()) {
            if (label == null) {
                FloatingActionButton(onClick = onClick) {
                    Icon(imageVector = icon, contentDescription = null)
                }
            } else {
                DkPillButton(
                    text = label,
                    icon = icon,
                    onClick = onClick,
                )
            }
        }
    }
}

@Immutable
data class DkFabMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** A toggle FAB that fans out into [items]. Picking one, or pressing back, folds it again. */
@Composable
fun DkFabMenu(
    modifier: Modifier = Modifier,
    items: List<DkFabMenuItem>,
    contentDescription: String? = null,
    visible: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = expanded) { expanded = false }

    AnimatedFab(visible = visible) {
        FloatingActionButtonMenu(
            modifier = modifier.reportFabSize(),
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                ) {
                    val progress = { checkedProgress }
                    Icon(
                        modifier = Modifier.animateIcon(progress),
                        imageVector = if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = contentDescription,
                    )
                }
            },
        ) {
            items.forEach { item ->
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    text = { Text(text = item.label) },
                )
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) expanded = false
    }
}

@Composable
private fun AnimatedFab(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        content()
        // Inside, so the reserved space goes once the exit has played out.
        ReleaseFabSize()
    }
}

@Composable
private fun Modifier.reportFabSize(): Modifier {
    val density = LocalDensity.current
    val state = LocalFabHostState.current

    return onSizeChanged {
        with(density) {
            state?.setup(DpSize(width = it.width.toDp(), height = it.height.toDp()))
        }
    }
}

@Composable
private fun ReleaseFabSize() {
    val state = LocalFabHostState.current

    DisposableEffect(Unit) {
        onDispose {
            state?.setup(DpSize.Zero)
        }
    }
}
