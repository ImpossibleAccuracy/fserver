package com.fserver.app.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    val density = LocalDensity.current
    val state = LocalFabHostState.current

    if (visible) {
        Box(
            modifier = modifier
                .onSizeChanged {
                    with(density) {
                        state?.setup(
                            DpSize(
                                width = it.width.toDp(),
                                height = it.height.toDp(),
                            )
                        )
                    }
                },
        ) {
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

    DisposableEffect(Unit) {
        onDispose {
            state?.setup(DpSize.Zero)
        }
    }
}
