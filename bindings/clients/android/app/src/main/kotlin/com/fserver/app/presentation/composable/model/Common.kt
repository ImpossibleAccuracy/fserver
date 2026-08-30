package com.fserver.app.presentation.composable.model

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.fserver.app.R
import com.fserver.core.files.model.FileSize
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind

@get:StringRes
val AuthMethod.labelRes: Int
    get() = when (this) {
        AuthMethod.ConfirmFingerprint -> R.string.auth_method_confirm_fingerprint
        AuthMethod.NearbySas -> R.string.auth_method_nearby_sas
        AuthMethod.Password -> R.string.auth_method_password
    }

val DeviceKind?.icon: ImageVector
    get() = when (this) {
        DeviceKind.Desktop -> Icons.Default.Computer
        DeviceKind.Laptop -> Icons.Default.LaptopMac
        DeviceKind.Phone -> Icons.Default.Smartphone
        DeviceKind.Tablet -> Icons.Default.TabletMac
        DeviceKind.Nas -> Icons.Default.Storage
        null -> Icons.Default.DeviceUnknown
    }

@Composable
fun FileSize.formatted(): String {
    val context = LocalContext.current

    return remember(this) {
        Formatter.formatShortFileSize(context, bytes)
    }
}
