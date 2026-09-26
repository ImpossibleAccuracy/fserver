package com.fserver.app.presentation.screens.settings.onetimecode.model

sealed interface OneTimeCodeIntent {
    data object NewCode : OneTimeCodeIntent
}
