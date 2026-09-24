package com.fserver.app.presentation.screens.settings.mydevice.model

sealed interface MyDeviceIntent {
    data class Renamed(val name: String) : MyDeviceIntent
}
