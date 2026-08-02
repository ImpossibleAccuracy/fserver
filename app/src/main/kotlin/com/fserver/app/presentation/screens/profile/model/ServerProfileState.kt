package com.fserver.app.presentation.screens.profile.model

import com.fserver.app.presentation.model.ServerProfileUi

data class ServerProfileState(
    val profile: ServerProfileUi,
    val password: String = "",
)