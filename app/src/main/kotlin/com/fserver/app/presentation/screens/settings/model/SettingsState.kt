package com.fserver.app.presentation.screens.settings.model

data class SettingsState(
    val deviceCount: Int = 0,
    val downloadFolder: String = "",
    val wifiOnly: Boolean = true,
    val compressOnTheFly: Boolean = false,
    val storageEncryptionOn: Boolean = true,
)