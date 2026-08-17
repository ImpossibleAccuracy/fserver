package com.fserver.app.presentation.composable.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.core.network.device.model.DeviceKind

/**
 * Icon for a device kind. Shared by the discovery list and the connect screen so the same
 * box does not change shape between the row the user tapped and the screen that opens.
 */
val DeviceKind?.icon: ImageVector
    get() = when (this) {
        DeviceKind.Desktop -> Icons.Default.Computer
        DeviceKind.Laptop -> Icons.Default.LaptopMac
        DeviceKind.Phone -> Icons.Default.Smartphone
        DeviceKind.Tablet -> Icons.Default.TabletMac
        DeviceKind.Nas -> Icons.Default.Storage
        null -> Icons.Default.DeviceUnknown
    }
