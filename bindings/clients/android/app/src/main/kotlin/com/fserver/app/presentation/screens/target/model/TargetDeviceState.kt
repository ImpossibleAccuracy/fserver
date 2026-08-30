package com.fserver.app.presentation.screens.target.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.device.model.DeviceKind

/**
 * Which connected device a source being configured will feed.
 *
 * Only devices with a live session can be chosen: anything merely discovered has to be paired
 * first, which is what "add a device" is for. A device that is known but offline stays in the
 * list and reads as unavailable rather than disappearing — vanishing rows are how a user
 * concludes the app forgot their NAS.
 */
data class TargetDeviceState(
    val devices: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
) {
    val canContinue: Boolean
        get() = selectedDeviceId != null

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val address: String?,
        val online: Boolean = true,
    )

    companion object {
        val SampleDevices = listOf(
            DeviceUi(
                id = "home-nas",
                name = "HOME-NAS",
                kind = DeviceKind.Nas,
                address = "192.168.1.42:8384",
            ),
            DeviceUi(
                id = "work-laptop",
                name = "WORK-LAPTOP",
                kind = DeviceKind.Laptop,
                address = "192.168.1.17:8384",
            ),
            DeviceUi(
                id = "studio-pc",
                name = "STUDIO-PC",
                kind = DeviceKind.Desktop,
                address = null,
                online = false,
            ),
        )
    }
}
