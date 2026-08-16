package com.fserver.app.presentation.screens.pairing.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class PairingState(
    /** Null while the greeting is still in flight, or once it has failed. */
    val device: DeviceUi? = null,
    val rememberDevice: Boolean = true,
    val isConnecting: Boolean = false,
    val password: String? = null,
    /** Why the device could not be reached, or why the connection attempt failed. */
    val error: String? = null,
) {
    @Immutable
    data class DeviceUi(
        /**
         * Null when [id] didn't come from discovery and hasn't resolved to one yet — a manual
         * address or QR code, before or while it's being probed. Shown once known, never
         * fabricated: an unresolved peer gets no name or kind guessed on its behalf.
         */
        val identity: IdentityUi?,
        /** Null only when nothing about the address is known yet either — mid-QR-decode. */
        val address: String?,
        /** Protocol version range the device offered, from the public greeting. */
        val protocolLine: String,
        val offeredMethods: List<AuthMethod>,
        val selectedMethod: AuthMethod?,
        /** Empty until a session is actually up — the greeting proves nothing by itself. */
        val fingerprintGroups: List<String>,
    ) {
        @Immutable
        data class IdentityUi(
            val name: String,
            val kind: DeviceKind?,
        )
    }

    /**
     * Guards only what the screen can actually check — that a greeting came back and a method is
     * picked. The fingerprint comparison is not in here: it happens in the user's head, and the
     * button must not imply the app verified it.
     */
    val canConnect: Boolean
        get() = device != null && !isConnecting

    companion object {
        val SampleDevice = DeviceUi(
            identity = DeviceUi.IdentityUi(
                name = "MacBook-Pro.local",
                kind = DeviceKind.Laptop,
            ),
            address = "192.168.1.14:8384",
            protocolLine = "protocol v1",
            offeredMethods = listOf(AuthMethod.ConfirmFingerprint),
            selectedMethod = AuthMethod.ConfirmFingerprint,
            fingerprintGroups = listOf("9f2c 4a01", "b7d3 e820", "15aa cc94", "0f6b 7e31"),
        )
    }
}
