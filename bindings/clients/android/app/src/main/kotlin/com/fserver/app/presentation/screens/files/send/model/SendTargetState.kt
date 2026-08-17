package com.fserver.app.presentation.screens.files.send.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.device.model.DeviceKind

/**
 * Who to send the picked files to.
 *
 * Only devices with a live session are listed: sending needs one, and anything merely discovered
 * has to go through pairing first — which is what the connect routes below the list are for.
 */
data class SendTargetState(
    val fileCount: Int = 0,
    val fileNames: List<String> = emptyList(),
    val devices: List<DeviceUi> = emptyList(),
    val confirmation: ConfirmationUi? = null,
    /** The selection is gone (process death took the store with it), so there is nothing to send. */
    val isSelectionLost: Boolean = false,
) {
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
        )
    }
}
