package com.fserver.app.presentation.screens.source.access.model

import com.fserver.app.presentation.composable.model.SourceAccessUi

sealed interface SourceAccessUiEffect {
    data class NavigateToMode(val access: SourceAccessUi) : SourceAccessUiEffect

    /** The whole-device branch has no dialog to raise — the toggle lives in system settings. */
    data object OpenSystemSettings : SourceAccessUiEffect
}
