package com.fserver.app.presentation.screens.pairing.model

import androidx.compose.runtime.Immutable
import com.fserver.core.domain.model.DeviceConnectionCapabilities
import com.fserver.core.domain.model.FoundDevice

@Immutable
data class PairingState(
    /** Null while the device is being looked up, or once it has dropped off the network. */
    val device: DeviceUi? = null,
    val password: String = "",
    val rememberDevice: Boolean = true,
) {
    @Immutable
    data class DeviceUi(
        val name: String,
        val kind: FoundDevice.Kind,
        val access: DeviceConnectionCapabilities.Access,
        val address: String,
        val technicalLine: String,
        val fingerprintGroups: List<String>,
    )

    /** The server asks for a secret only in some access modes; the field follows that. */
    val requiresPassword: Boolean
        get() = device?.access == DeviceConnectionCapabilities.Access.Password

    /**
     * Guards only what the screen can actually check — that the device is still there and a
     * password was typed where one is demanded. The fingerprint comparison is not in here:
     * it happens in the user's head, and the button must not imply the app verified it.
     */
    val canConnect: Boolean
        get() = device != null && (!requiresPassword || password.isNotBlank())

    companion object {
        val SampleDevice = DeviceUi(
            name = "MacBook-Pro.local",
            kind = FoundDevice.Kind.Laptop,
            access = DeviceConnectionCapabilities.Access.Open,
            address = "192.168.1.14:8384",
            technicalLine = "TLS 1.3 · protocol v1",
            fingerprintGroups = listOf("9f2c 4a01", "b7d3 e820", "15aa cc94", "0f6b 7e31"),
        )
    }
}
