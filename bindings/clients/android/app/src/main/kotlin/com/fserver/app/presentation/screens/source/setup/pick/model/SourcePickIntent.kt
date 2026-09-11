package com.fserver.app.presentation.screens.source.setup.pick.model

sealed interface SourcePickIntent {
    data object MoreToggled : SourcePickIntent
}
