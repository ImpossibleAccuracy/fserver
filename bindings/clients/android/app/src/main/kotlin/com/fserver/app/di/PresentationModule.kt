package com.fserver.app.di

import com.fserver.app.data.DemoContentSource
import com.fserver.app.data.SampleContentSource
import com.fserver.app.playback.BackgroundPlayback
import com.fserver.app.presentation.shared.error.ErrorBus
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.navigation.AppViewModel
import com.fserver.app.presentation.screens.activity.ActivityViewModel
import com.fserver.app.presentation.screens.diagnostics.DiagnosticsViewModel
import com.fserver.app.presentation.screens.discovery.connect.ConnectViewModel
import com.fserver.app.presentation.screens.discovery.manual.ManualAddressViewModel
import com.fserver.app.presentation.screens.discovery.qr.QrScanViewModel
import com.fserver.app.presentation.screens.dashboard.DashboardViewModel
import com.fserver.app.presentation.screens.files.FilesViewModel
import com.fserver.app.presentation.screens.files.editor.ImageEditorViewModel
import com.fserver.app.presentation.screens.onboarding.OnboardingViewModel
import com.fserver.app.presentation.screens.device.pairing.PairingViewModel
import com.fserver.app.presentation.screens.source.list.SyncRequestListViewModel
import com.fserver.app.presentation.screens.source.request.SyncRequestViewModel
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupViewModel
import com.fserver.app.presentation.screens.source.details.SourceDetailsViewModel
import com.fserver.app.presentation.screens.source.edit.SourceEditViewModel
import com.fserver.app.presentation.screens.source.shared.done.SourceDoneViewModel
import com.fserver.app.presentation.screens.source.shared.progress.SourceProgressViewModel
import com.fserver.app.presentation.screens.device.details.DeviceDetailsViewModel
import com.fserver.app.presentation.screens.settings.devices.DevicesViewModel
import com.fserver.app.presentation.screens.settings.mydevice.MyDeviceViewModel
import com.fserver.app.presentation.screens.settings.onetimecode.OneTimeCodeViewModel
import com.fserver.app.presentation.screens.settings.pin.PinChangeViewModel
import com.fserver.app.presentation.screens.settings.security.SecurityViewModel
import com.fserver.app.presentation.screens.settings.storage.main.StorageViewModel
import com.fserver.app.presentation.screens.settings.storage.source.StorageSourceViewModel
import org.koin.android.ext.koin.androidContext
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
    single { BackgroundPlayback(androidContext()) }

    // One bus for the whole app: `AppViewModel` reads it, everything else only writes. Bound
    // under both types so a screen cannot take a dependency on the reading half.
    single<ErrorBus> { ErrorBus() }
    single<ErrorReporter> { get<ErrorBus>() }

    viewModelOf(::AppViewModel)

    viewModelOf(::OnboardingViewModel)
    viewModelOf(::ConnectViewModel)
    viewModelOf(::QrScanViewModel)
    viewModelOf(::ManualAddressViewModel)
    viewModelOf(::PairingViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::FilesViewModel)
    viewModelOf(::ImageEditorViewModel)

    viewModelOf(::SourceSetupViewModel)
    viewModelOf(::SyncRequestListViewModel)
    viewModelOf(::SyncRequestViewModel)
    viewModelOf(::SourceProgressViewModel)
    viewModelOf(::SourceDoneViewModel)
    viewModelOf(::SourceDetailsViewModel)
    viewModelOf(::SourceEditViewModel)

    viewModelOf(::ActivityViewModel)
    viewModelOf(::DiagnosticsViewModel)

    // Settings subtree; the root itself is stateless and has no ViewModel.
    viewModelOf(::MyDeviceViewModel)
    viewModelOf(::DevicesViewModel)
    viewModelOf(::DeviceDetailsViewModel)
    viewModelOf(::SecurityViewModel)
    viewModelOf(::PinChangeViewModel)
    viewModelOf(::OneTimeCodeViewModel)
    viewModelOf(::StorageViewModel)
    viewModelOf(::StorageSourceViewModel)
}
