package com.fserver.app.presentation.screens.settings.mydevice.model

import com.fserver.core.network.TransportKind

data class MyDeviceState(
    val name: String = "",
    val invitation: InvitationUi = InvitationUi.Loading,
) {
    /**
     * This device's own connection code. [Loading] is the state the sheet opens in: the code takes
     * a listener and an identity read to assemble, and an empty sheet reads as a broken one.
     */
    sealed interface InvitationUi {
        data object Loading : InvitationUi

        data object Unavailable : InvitationUi

        data class Ready(
            val payload: String,
            val addresses: List<AddressUi>,
            val fingerprintGroups: List<String>,
        ) : InvitationUi
    }

    data class AddressUi(
        val address: String,
        val transport: TransportKind?,
    )

    companion object {
        val SampleInvitation = InvitationUi.Ready(
            payload = """{"ip":"192.168.1.42","port":29470,"deviceId":"a1","nearby":true}""",
            addresses = listOf(AddressUi("192.168.1.42:29470", TransportKind.MulticastDns)),
            fingerprintGroups = listOf("9f2c", "4a01", "b7d3", "e820"),
        )
    }
}
