package com.fserver.app.presentation.screens.discovery.manual.model

sealed interface ManualAddressIntent {
    data class HostChanged(val host: String) : ManualAddressIntent

    data class PortChanged(val port: String) : ManualAddressIntent

    data object ConnectClicked : ManualAddressIntent

    /** The sheet has navigated on; drop the result so reopening it does not re-fire. */
    data object ResultConsumed : ManualAddressIntent
}
