package com.fserver.core.sync.model

/**
 * Sync settings shared by every source. Per-source behaviour is [SyncMode], not this.
 */
data class SyncPreferences(
    val deviceConstraints: DeviceConstraints,
    val conflictResolution: ConflictResolution,
) {
    enum class ConflictResolution {
        /** Keep only the last modified file. */
        LastWriteWins,

        /** Keep both files in `.conflicts` folder. */
        KeepBoth,
    }

    data class DeviceConstraints(
        val wifiRequired: Boolean,
        val chargingRequired: Boolean,
    )

    companion object {
        val Default = SyncPreferences(
            deviceConstraints = DeviceConstraints(
                wifiRequired = false,
                chargingRequired = false,
            ),
            conflictResolution = ConflictResolution.LastWriteWins,
        )
    }
}
