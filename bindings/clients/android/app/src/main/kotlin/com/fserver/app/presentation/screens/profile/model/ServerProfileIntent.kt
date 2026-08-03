package com.fserver.app.presentation.screens.profile.model

sealed interface ServerProfileIntent {
    data class PasswordChanged(val password: String) : ServerProfileIntent
}
