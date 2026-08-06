package com.fserver.app.di

import com.fserver.app.data.DemoContentSource
import com.fserver.app.data.SampleContentSource
import com.fserver.app.presentation.screens.diagnostics.DiagnosticsViewModel
import com.fserver.app.presentation.screens.discovery.DeviceDiscoveryViewModel
import com.fserver.app.presentation.screens.files.list.FilesViewModel
import com.fserver.app.presentation.screens.files.picker.FilesPickerViewModel
import com.fserver.app.presentation.screens.onboarding.OnboardingViewModel
import com.fserver.app.presentation.screens.pairing.PairingViewModel
import com.fserver.app.presentation.screens.profile.ServerProfileViewModel
import com.fserver.app.presentation.screens.qr.QrScanViewModel
import com.fserver.app.presentation.screens.settings.SettingsViewModel
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

    viewModelOf(::OnboardingViewModel)
    viewModelOf(::DeviceDiscoveryViewModel)
    viewModelOf(::PairingViewModel)
    viewModelOf(::QrScanViewModel)
    viewModelOf(::ServerProfileViewModel)
    viewModelOf(::FilesViewModel)
    viewModelOf(::FilesPickerViewModel)
    viewModelOf(::TransfersViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DiagnosticsViewModel)
}
