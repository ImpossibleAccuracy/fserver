package com.fserver.app.presentation.composable.model

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.common.model.FileSize
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.sync.progress.FileTransfer
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@get:StringRes
val AuthMethod.labelRes: Int
    get() = when (this) {
        AuthMethod.ConfirmFingerprint -> R.string.auth_method_confirm_fingerprint
        AuthMethod.NearbySas -> R.string.auth_method_nearby_sas
        AuthMethod.Password -> R.string.auth_method_password
        AuthMethod.Pin -> R.string.auth_method_pin
        AuthMethod.OneTimeCode -> R.string.auth_method_one_time_code
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

/** `null` for a file that is simply here: the common case earns no marker. */
val FileAvailabilityUi.icon: ImageVector?
    get() = when (this) {
        FileAvailabilityUi.OnDevice -> null
        FileAvailabilityUi.Offloaded -> Icons.Default.Cloud
        FileAvailabilityUi.OnPeer -> Icons.Default.Lock
        FileAvailabilityUi.OnServer -> Icons.Default.CloudDownload
    }

@get:StringRes
val FileAvailabilityUi.labelRes: Int
    get() = when (this) {
        FileAvailabilityUi.OnDevice -> R.string.files_state_local
        FileAvailabilityUi.Offloaded -> R.string.files_state_offloaded
        FileAvailabilityUi.OnPeer -> R.string.files_state_on_peer
        FileAvailabilityUi.OnServer -> R.string.files_state_remote
    }

@Composable
fun FileSize.formatted(): String {
    val context = LocalContext.current

    return remember(this) {
        Formatter.formatShortFileSize(context, bytes)
    }
}

/**
 * Limits are stored in binary units (1 GB = 1024³ B), but [formatted] prints SI units.
 * Rescales so a binary "2 GB" prints as "2 GB" rather than "2.1 GB".
 */
fun FileSize.binaryToDecimal(): FileSize {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < 4) {
        value /= 1024
        unit++
    }
    return FileSize((value * 1000.0.pow(unit)).roundToLong())
}

/** "yesterday", "3 days ago", then a plain date once that stops being useful. */
@Composable
fun Instant.formatted(minResolution: Long = DateUtils.DAY_IN_MILLIS): String = remember(this) {
    DateUtils.getRelativeTimeSpanString(
        toEpochMilliseconds(),
        System.currentTimeMillis(),
        minResolution,
    ).toString()
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** A plain localized date, "12 Jun 2026". */
fun Instant.dateLabel(): String = dateFormat.format(toJavaInstant().atZone(ZoneId.systemDefault()))

/** The last path segment. */
fun String.fileName(): String = substringAfterLast('/')

val FileTransfer.Direction.icon: ImageVector
    get() = when (this) {
        FileTransfer.Direction.Outgoing -> Icons.Default.ArrowUpward
        FileTransfer.Direction.Incoming -> Icons.Default.ArrowDownward
    }

@get:StringRes
val FileTransfer.Direction.labelRes: Int
    get() = when (this) {
        FileTransfer.Direction.Outgoing -> R.string.transfer_direction_outgoing
        FileTransfer.Direction.Incoming -> R.string.transfer_direction_incoming
    }

/** "41 MB/s", or a dash while too little has moved to divide by. */
@Composable
fun rateFormatted(bytesPerSecond: Long?): String =
    if (bytesPerSecond == null) stringResource(R.string.value_unknown)
    else stringResource(R.string.transfer_speed, FileSize(bytesPerSecond).formatted())

/** Rounded to the biggest unit that still reads as a wait: "18 s", "4 min", "2 h". */
@Composable
fun Duration?.etaFormatted(): String = when {
    this == null -> stringResource(R.string.value_unknown)
    inWholeMinutes < 1 -> stringResource(R.string.duration_seconds, inWholeSeconds)
    inWholeHours < 1 -> stringResource(R.string.duration_minutes, inWholeMinutes)
    else -> stringResource(R.string.duration_hours, inWholeHours)
}
