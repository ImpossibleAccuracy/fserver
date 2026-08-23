package com.fserver.app.presentation.screens.target.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.model.TargetPurpose
import com.fserver.core.network.device.model.DeviceKind

/**
 * Where files go — a selection from the picker, or everything a configured source will produce.
 *
 * Only devices with a live session can be chosen: sending needs one, and anything merely
 * discovered has to be paired first, which is what "add a device" is for. A device that is
 * known but offline stays in the list and reads as unavailable rather than disappearing —
 * vanishing rows are how a user concludes the app forgot their NAS.
 */
data class TargetDeviceState(
    val purpose: TargetPurpose,
    val fileCount: Int = 0,
    val fileNames: List<String> = emptyList(),
    val devices: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val confirmation: ConfirmationUi? = null,
    /** The selection is gone (process death took the store with it), so there is nothing to send. */
    val isSelectionLost: Boolean = false,
) {
    val isConfiguringSource: Boolean
        get() = purpose is TargetPurpose.ConfigureSource

    val canContinue: Boolean
        get() = selectedDeviceId != null

    /** Summary line: the first few names, and how many more there are. */
    val previewLine: String
        get() = when {
            fileNames.isEmpty() -> ""
            fileNames.size <= PreviewNames -> fileNames.joinToString(", ")
            else -> fileNames.take(PreviewNames).joinToString(", ") +
                    " +${fileNames.size - PreviewNames}"
        }

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val address: String?,
        val online: Boolean = true,
    )

    @Immutable
    data class ConfirmationUi(
        val deviceId: String,
        val deviceName: String,
        val fileCount: Int,
    )

    companion object {
        private const val PreviewNames = 3

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
