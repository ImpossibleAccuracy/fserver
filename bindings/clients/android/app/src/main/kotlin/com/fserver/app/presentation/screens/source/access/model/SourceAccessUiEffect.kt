package com.fserver.app.presentation.screens.source.access.model

import com.fserver.app.presentation.screens.source.shared.SourceAccessUi

sealed interface SourceAccessUiEffect {
    data class NavigateToMode(val access: SourceAccessUi) : SourceAccessUiEffect
}
