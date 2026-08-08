package com.fserver.app.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.designkit.DkNavigationBar
import com.fserver.app.presentation.designkit.DkNavigationBarItem
import com.fserver.app.presentation.designkit.createBottomBarState
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.TopLevelDestination

@Composable
fun AppStyling(
    navigator: AppNavigator,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current

    val snackbarState = remember { SnackbarHostState() }
    val fabState = remember { DkFabHostState() }

    val navigationBarInsets = WindowInsets.navigationBars.asPaddingValues()
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val isOnTopRoute = navigator.isSectionRootVisible && TopLevelDestination.entries.any {
                it.destination == navigator.activeSection
            }

            if (!isOnTopRoute) {
                bottomBarHeight = 0.dp
                return@Scaffold
            }

            DkNavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        with(density) {
                            bottomBarHeight =
                                it.height.toDp() - navigationBarInsets.calculateBottomPadding()
                        }
                    }
            ) {
                TopLevelDestination.entries.forEach { tab ->
                    val isSelected = tab.destination == navigator.activeSection

                    DkNavigationBarItem(
                        label = stringResource(tab.label),
                        icon = tab.icon,
                        selected = isSelected,
                        onClick = {
                            navigator.navigate(tab.destination)
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.size(fabState.size)
            )
        },
        snackbarHost = {
            DkSnackbar(state = snackbarState)
        }
    ) { paddingValues ->
        CompositionLocalProvider(
            createFabHostState(fabState),
            createBottomBarState(bottomBarHeight),
            createSnackbarControllerState(snackbarState),
        ) {
            content(paddingValues)
        }
    }
}
