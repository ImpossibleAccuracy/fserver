package com.fserver.app.presentation.composable.model

import androidx.compose.runtime.Immutable
import com.fserver.core.disk.DiskUsage

/** How full the phone is, and how much of it this app accounts for. */
@Immutable
data class StorageUsageUi(
    val totalBytes: Long,
    val freeBytes: Long,
    val appBytes: Long,
    val remoteOnlyFiles: Int = 0,
    val remoteOnlyBytes: Long = 0,
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(0)

    val otherBytes: Long
        get() = (usedBytes - appBytes).coerceAtLeast(0)

    companion object {
        val Sample = StorageUsageUi(
            totalBytes = 128_000_000_000,
            freeBytes = 20_000_000_000,
            appBytes = 23_000_000_000,
            remoteOnlyFiles = 1240,
            remoteOnlyBytes = 4_200_000_000,
        )
    }
}

/** "App" is what installing and syncing cost: the app's own footprint, plus every indexed file still here. */
fun DiskUsage.toUi(
    indexedBytes: Long,
    remoteOnlyFiles: Int = 0,
    remoteOnlyBytes: Long = 0,
) = StorageUsageUi(
    totalBytes = totalBytes,
    freeBytes = freeBytes,
    appBytes = footprint.totalBytes + indexedBytes,
    remoteOnlyFiles = remoteOnlyFiles,
    remoteOnlyBytes = remoteOnlyBytes,
)
