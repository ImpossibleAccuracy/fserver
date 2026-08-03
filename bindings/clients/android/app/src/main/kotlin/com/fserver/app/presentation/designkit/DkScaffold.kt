package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalBottomBarHeight = compositionLocalOf { 0.dp }

@Composable
fun createBottomBarState(
    height: Dp,
) = LocalBottomBarHeight provides height

@Composable
fun DkScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = {
            val bottomBarHeight = LocalBottomBarHeight.current

            Box(
                modifier = Modifier.padding(bottom = bottomBarHeight)
            ) {
                floatingActionButton()
            }
        },
        content = { paddings ->
            val bottomBarHeight = LocalBottomBarHeight.current

            content(
                paddings.plus(
                    PaddingValues(
                        bottom = bottomBarHeight
                    )
                )
            )
        },
    )
}
