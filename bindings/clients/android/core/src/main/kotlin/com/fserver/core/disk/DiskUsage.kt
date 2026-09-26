package com.fserver.core.disk

/** How full the device's own storage is, and how much of it this app accounts for outside its index. */
data class DiskUsage(
    val totalBytes: Long,
    val freeBytes: Long,
    val footprint: AppFootprint,
)

/**
 * The app's own bytes, by what dropping them would cost. Synced files are not here: the index
 * already counts them, wherever they live - including sources hosted in app-private storage.
 */
data class AppFootprint(
    /** The installed APK and its splits, or null when the platform will not say. */
    val apkBytes: Long?,
    /** Transfers not yet placed into their source. Dropping them restarts those transfers. */
    val stagingBytes: Long,
    /** Thumbnails, previews and exports: rebuilt on demand. */
    val cacheBytes: Long,
    /** Index, settings and keys. Not something to free. */
    val serviceBytes: Long,
) {
    val totalBytes: Long
        get() = (apkBytes ?: 0) + stagingBytes + cacheBytes + serviceBytes
}
