package com.fserver.app.di

import com.fserver.app.data.DemoContentSource
import com.fserver.app.data.SampleContentSource
import com.fserver.app.presentation.navigation.AppViewModel
import com.fserver.app.presentation.screens.diagnostics.DiagnosticsViewModel
import com.fserver.app.presentation.screens.discovery.automatic.DeviceDiscoveryViewModel
import com.fserver.app.presentation.screens.discovery.hub.ConnectHubViewModel
import com.fserver.app.presentation.screens.discovery.manual.ManualAddressViewModel
import com.fserver.app.presentation.screens.discovery.qr.QrScanViewModel
import com.fserver.app.presentation.screens.files.list.FilesViewModel
import com.fserver.app.presentation.screens.onboarding.OnboardingViewModel
import com.fserver.app.presentation.screens.pairing.PairingViewModel
import com.fserver.app.presentation.screens.source.request.details.SyncRequestDetailsViewModel
import com.fserver.app.presentation.screens.source.request.location.SyncRequestLocationViewModel
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupViewModel
import com.fserver.app.presentation.screens.source.shared.done.SourceDoneViewModel
import com.fserver.app.presentation.screens.source.shared.progress.SourceProgressViewModel
import com.fserver.app.presentation.screens.settings.details.DeviceDetailsViewModel
import com.fserver.app.presentation.screens.settings.devices.DevicesViewModel
import com.fserver.app.presentation.screens.settings.pin.PinChangeViewModel
import com.fserver.app.presentation.screens.settings.security.SecurityViewModel
import com.fserver.app.presentation.screens.transfers.TransfersViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * One ViewModel per screen, plus the content seam they read through.
 *
 * [DemoContentSource] is bound to fixtures today; when `:core` lands, its implementation
 * is swapped here and no screen or ViewModel signature changes.
 */
val presentationModule = module {
    single<DemoContentSource> { SampleContentSource() }

    viewModelOf(::AppViewModel)

    viewModelOf(::OnboardingViewModel)
    viewModelOf(::ConnectHubViewModel)
    viewModelOf(::DeviceDiscoveryViewModel)
    viewModelOf(::QrScanViewModel)
    viewModelOf(::ManualAddressViewModel)
    viewModelOf(::PairingViewModel)
    viewModelOf(::FilesViewModel)

    viewModelOf(::SourceSetupViewModel)
    viewModelOf(::SyncRequestDetailsViewModel)
    viewModelOf(::SyncRequestLocationViewModel)
    viewModelOf(::SourceProgressViewModel)
    viewModelOf(::SourceDoneViewModel)

    viewModelOf(::TransfersViewModel)
    viewModelOf(::DiagnosticsViewModel)

    // Settings subtree; the root itself is stateless and has no ViewModel.
    viewModelOf(::DevicesViewModel)
    viewModelOf(::DeviceDetailsViewModel)
    viewModelOf(::SecurityViewModel)
    viewModelOf(::PinChangeViewModel)
}
