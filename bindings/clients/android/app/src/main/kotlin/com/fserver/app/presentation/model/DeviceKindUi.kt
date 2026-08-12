package com.fserver.app.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.net.discovery.DiscoveredPeer

/**
 * Icon for a device kind. Shared by the discovery list and the connect screen so the same
 * box does not change shape between the row the user tapped and the screen that opens.
 */
val DiscoveredPeer.Kind?.icon: ImageVector
    get() = when (this) {
        DiscoveredPeer.Kind.Desktop -> Icons.Default.Computer
        DiscoveredPeer.Kind.Laptop -> Icons.Default.LaptopMac
        DiscoveredPeer.Kind.Phone -> Icons.Default.Smartphone
        DiscoveredPeer.Kind.Tablet -> Icons.Default.TabletMac
        DiscoveredPeer.Kind.Nas -> Icons.Default.Storage
        null -> Icons.Default.DeviceUnknown
    }
