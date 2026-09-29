package com.fserver.core.sync.metadata

import com.fserver.core.sync.limits.SourceUsage
import kotlin.time.Instant

/**
 * One device's half of a source, as that device last reported it - this device's own or the peer's.
 * Informational only: shown to the user, never read by any engine decision.
 */
data class PeerSourceMetadata(
    val sourceId: String,
    /** Whose half this is. */
    val deviceId: String,
    /** What kind of place the device keeps the files in. */
    val storageKind: StorageKind,
    /** The device's directory as a person reads it. Set for [StorageKind.Folder] only. */
    val storagePath: String?,
    /** Files the device holds for the source, and their total size. */
    val usage: SourceUsage,
    /** How much of the device's own file limits [usage] takes, in percent; null when it sets none. */
    val usedPercent: Float?,
    val updatedAt: Instant,
) {
    enum class StorageKind {
        /** A directory the user picked: [storagePath] says which. */
        Folder,

        /** The app's private storage. */
        AppStorage,

        /** The device's media library: a collection, not a directory. */
        Media,
    }
}
