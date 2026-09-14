package com.fserver.app.presentation.screens.discovery.connect.model

/**
 * What the connect screen hands back: the device the user picked, already connected.
 *
 * Travels over [com.fserver.app.presentation.navigation.ResultEventBus], so the screen stays the
 * same one whether it was opened to connect for the first time or to answer "where to send?".
 */
data class DeviceSelection(val deviceId: String)
