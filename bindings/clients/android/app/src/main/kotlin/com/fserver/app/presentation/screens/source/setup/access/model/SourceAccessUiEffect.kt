package com.fserver.app.presentation.screens.source.setup.access.model

sealed interface SourceAccessUiEffect {
    /** The confirmed folder finished its second walk — there is nothing left to look at. */
    data object NavigateToMode : SourceAccessUiEffect
}
