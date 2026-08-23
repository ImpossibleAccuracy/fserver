package com.fserver.app.presentation.screens.source.pick.model

sealed interface SourcePickIntent {
    data object MoreToggled : SourcePickIntent
}
