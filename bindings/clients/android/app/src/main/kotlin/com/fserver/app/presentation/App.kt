package com.fserver.app.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.fserver.app.presentation.composable.AppStyling
import com.fserver.app.presentation.composable.IncomingConnectionSheet
import com.fserver.app.presentation.composable.IncomingFilesSheet
import com.fserver.app.presentation.composable.PendingConfirmationDialog
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.AppViewModel
import com.fserver.app.presentation.navigation.model.AppRootIntent
import com.fserver.app.presentation.navigation.rememberAppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy
import com.fserver.app.presentation.screens.diagnostics.diagnosticEntry
import com.fserver.app.presentation.screens.discovery.automatic.deviceDiscoveryEntry
import com.fserver.app.presentation.screens.discovery.hub.connectHubEntry
import com.fserver.app.presentation.screens.discovery.manual.manualAddressEntry
import com.fserver.app.presentation.screens.discovery.qr.qrScanEntry
import com.fserver.app.presentation.screens.files.list.filesListEntry
import com.fserver.app.presentation.screens.files.picker.filesPickerEntry
import com.fserver.app.presentation.screens.files.send.sendTargetEntry
import com.fserver.app.presentation.screens.onboarding.onboardingEntry
import com.fserver.app.presentation.screens.pairing.pairingEntry
import com.fserver.app.presentation.screens.settings.settingsEntry
import com.fserver.app.presentation.screens.transfers.transfersEntry
import kotlinx.coroutines.flow.combine
import org.koin.androidx.compose.koinViewModel

// Material 3 emphasized, like sytem enter/exit anim
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// ~8% height offset for slide transitions
private fun smallOffset(full: Int) = (full * 0.08f).toInt()


@Composable
fun FServerApp(
    viewModel: AppViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val state = uiState ?: return

    val lifecycleOwner = LocalLifecycleOwner.current
    val navigator = rememberAppNavigator(state.startDestination)

    LaunchedEffect(lifecycleOwner, navigator) {
        val currentDestinationFlow = snapshotFlow { navigator.currentTopDestination }

        combine(
            lifecycleOwner.lifecycle.currentStateFlow,
            currentDestinationFlow,
            ::Pair
        )
            .collect { (lifecycle, destination) ->
                viewModel.onIntent(
                    AppRootIntent.ForegroundStateChanged(
                        lifecycle = lifecycle,
                        destination = destination,
                    )
                )
            }
    }

    AppStyling(
        navigator = navigator,
    ) {
        NavHostGraph(navigator = navigator)

        // Above the graph rather than inside it: a peer knocks whatever screen is open.
        state.incomingConnection?.let { request ->
            IncomingConnectionSheet(
                request = request,
                onAccept = {
                    viewModel.onIntent(AppRootIntent.AcceptIncomingConnection)
                },
                onDecline = {
                    viewModel.onIntent(AppRootIntent.RejectIncomingConnection)
                },
            )
        }

        // A transfer offer is not tied to the file list either: it arrives over whatever
        // screen is open, so it is answered here.
        state.incomingTransfer?.let { request ->
            IncomingFilesSheet(
                request = request,
                onAccept = {
                    viewModel.onIntent(AppRootIntent.AcceptIncomingTransfer)
                },
                onDecline = {
                    viewModel.onIntent(AppRootIntent.RejectIncomingTransfer)
                },
                // Dismissing decides nothing, so it declines: the sender is waiting on an answer.
                onDismiss = {
                    viewModel.onIntent(AppRootIntent.RejectIncomingTransfer)
                },
            )
        }

        // The actual code compare, mid-handshake. Can follow either sheet above, or a Connect
        // tapped on the pairing screen - it shows up wherever that call happens to be pending.
        state.pendingConfirmation?.let { request ->
            PendingConfirmationDialog(
                request = request,
                onConfirm = {
                    viewModel.onIntent(AppRootIntent.AcceptPendingConfirmation)
                },
                onReject = {
                    viewModel.onIntent(AppRootIntent.RejectPendingConfirmation)
                },
            )
        }
    }
}

@Composable
private fun NavHostGraph(navigator: AppNavigator) {
    NavDisplay(
        backStack = navigator.activeBackStack,
        onBack = { navigator.navigateUp() },
        sceneStrategies = listOf(
            remember { BottomSheetSceneStrategy() }
        ),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),

        transitionSpec = {
            slideInVertically(
                animationSpec = tween(400, easing = EmphasizedDecelerate),
                initialOffsetY = ::smallOffset
            )
                .plus(fadeIn(tween(250, delayMillis = 50, easing = EmphasizedDecelerate)))
                .togetherWith(
                    fadeOut(tween(200, easing = EmphasizedAccelerate))
                )
        },
        popTransitionSpec = {
            fadeIn(tween(250, easing = EmphasizedDecelerate))
                .togetherWith(
                    slideOutVertically(
                        animationSpec = tween(400, easing = EmphasizedAccelerate),
                        targetOffsetY = ::smallOffset
                    ).plus(fadeOut(tween(200, easing = EmphasizedAccelerate)))
                )
        },
        predictivePopTransitionSpec = {
            scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(400, easing = EmphasizedDecelerate)
            )
                .plus(fadeIn(tween(250, easing = EmphasizedDecelerate)))
                .togetherWith(
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(400, easing = EmphasizedAccelerate)
                    ).plus(fadeOut(tween(250, easing = EmphasizedAccelerate)))
                )
        },
        entryProvider = entryProvider {
            onboardingEntry(navigator)
            connectHubEntry(navigator)
            deviceDiscoveryEntry(navigator)
            qrScanEntry(navigator)
            manualAddressEntry(navigator)
            pairingEntry(navigator)
            transfersEntry()
            settingsEntry(navigator)
            diagnosticEntry(navigator)

            filesListEntry(navigator)
            filesPickerEntry(navigator)
            sendTargetEntry(navigator)
        },
    )
}
