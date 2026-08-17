package com.fserver.app.presentation.screens.files.send.model

sealed interface SendTargetIntent {
    /** A connected device was tapped — asks for confirmation rather than sending straight away. */
    data class DeviceSelected(val deviceId: String) : SendTargetIntent

    /**
     * The user left for one of the connect routes. Marks who was already connected, so whoever
     * shows up while they are away is the device they went to fetch.
     */
    data object ConnectRouteOpened : SendTargetIntent

    data object SendConfirmed : SendTargetIntent

    data object SendCancelled : SendTargetIntent
}
