package com.fserver.app.presentation.screens.target.model

sealed interface TargetDeviceIntent {
    /** A connected device was tapped. Picking is not committing — that is what continue is for. */
    data class DeviceSelected(val deviceId: String) : TargetDeviceIntent

    /**
     * The user left to add a device. Marks who was already connected, so whoever shows up while
     * they are away is the device they went to fetch and gets selected on their return.
     */
    data object ConnectRouteOpened : TargetDeviceIntent

    /** The target is chosen: confirm the send, or move on to the mode's conditions. */
    data object ContinueClicked : TargetDeviceIntent

    data object SendConfirmed : TargetDeviceIntent

    data object SendCancelled : TargetDeviceIntent
}
