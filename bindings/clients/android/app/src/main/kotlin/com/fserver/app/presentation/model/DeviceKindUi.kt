package com.fserver.app.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.net.peer.PeerDescriptor

/**
 * Icon for a device kind. Shared by the discovery list and the connect screen so the same
 * box does not change shape between the row the user tapped and the screen that opens.
 */
val PeerDescriptor.Kind?.icon: ImageVector
    get() = when (this) {
        PeerDescriptor.Kind.Desktop -> Icons.Default.Computer
        PeerDescriptor.Kind.Laptop -> Icons.Default.LaptopMac
        PeerDescriptor.Kind.Phone -> Icons.Default.Smartphone
        PeerDescriptor.Kind.Tablet -> Icons.Default.TabletMac
        PeerDescriptor.Kind.Nas -> Icons.Default.Storage
        null -> Icons.Default.DeviceUnknown
    }
