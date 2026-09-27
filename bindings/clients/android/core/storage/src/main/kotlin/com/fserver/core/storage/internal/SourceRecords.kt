package com.fserver.core.storage.internal

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SourceTombstone
import com.fserver.core.sync.model.SyncMode
import timber.log.Timber
import kotlin.time.Instant

/**
 * The one place that turns a [SourceEntry] into the `source` row plus its `attribute` rows and back.
 *
 * Both directions live together on purpose: the stored names and the stored encodings are a format,
 * and a reader that spells them even slightly differently from the writer loses a field silently.
 *
 * Discriminators are written out as literals rather than taken from `simpleName`, so renaming a
 * Kotlin class is a compile-time decision about the stored format instead of an accidental one.
 *
 * Reads are strict. A row whose attributes do not rebuild a source is skipped and logged - never
 * defaulted, repaired, or written back. Rows left behind by a renamed field are a schema migration's
 * job.
 */
internal object SourceRecords {
    // Attribute owners. A source, the request that became it and the tombstone that replaces it all
    // share an id, never an owner.
    const val OwnerSource = "source"
    const val OwnerTombstone = "source_tombstone"
    const val OwnerRequest = "source_request"

    const val Location = "location"
    const val Mode = "mode"
    const val Status = "status"
    const val Preferences = "preferences"

    // Location discriminators. Root is absent: it is not SourceLocation.Persistable.
    private const val Tree = "Tree"
    private const val Media = "Media"
    private const val Directory = "Directory"
    private const val Internal = "Internal"

    // Mode discriminators.
    private const val Mirror = "Mirror"
    private const val AutoUpload = "AutoUpload"
    private const val Offload = "Offload"

    // Evict policy discriminators, stored under the mode's own `policy` field.
    private const val OlderThanDays = "OlderThanDays"
    private const val LargerThanBytes = "LargerThanBytes"

    // Status discriminators.
    private const val Pending = "Pending"
    private const val Active = "Active"
    private const val Disabled = "Disabled"

    // Field names, by the type that owns them.
    private const val Path = "path"
    private const val Bucket = "bucket"
    private const val IgnoreFilesBefore = "ignoreFilesBefore"
    private const val KeepPinned = "keepPinned"
    private const val Policy = "policy"
    private const val PolicyDays = "policy.days"
    private const val PolicyBytes = "policy.bytes"
    private const val Reason = "reason"
    private const val WifiRequired = "wifiRequired"
    private const val ChargingRequired = "chargingRequired"
    private const val ConflictResolution = "conflictResolution"
    private const val MaxFiles = "maxFiles"
    private const val MaxTotalBytes = "maxTotalBytes"

    // Conflict resolution discriminators.
    private const val LastWriteWins = "LastWriteWins"
    private const val KeepBoth = "KeepBoth"

    // ---------------- discriminators, for the row's own columns ----------------

    fun discriminatorOf(location: SourceLocation.Persistable): String = when (location) {
        is SourceLocation.Tree -> Tree
        SourceLocation.Media -> Media
        is SourceLocation.Directory -> Directory
        is SourceLocation.Internal -> Internal
    }

    fun discriminatorOf(mode: SyncMode): String = when (mode) {
        is SyncMode.Mirror -> Mirror
        is SyncMode.AutoUpload -> AutoUpload
        is SyncMode.Offload -> Offload
    }

    fun discriminatorOf(status: SourceEntry.Status): String = when (status) {
        SourceEntry.Status.Pending -> Pending
        SourceEntry.Status.Active -> Active
        is SourceEntry.Status.Disabled -> Disabled
    }

    // ---------------- writing ----------------

    /** Every attribute row [source] needs, the variant parts only. */
    fun attributesOf(source: SourceEntry): List<Attribute> = Writer()
        .apply {
            writeLocation(source.location)
            writeMode(source.syncMode)
            writeStatus(source.status)
            writePreferences(source.preferences)
        }
        .rows

    /** The `status` attributes alone, for a status change that leaves the rest of the record alone. */
    fun statusAttributesOf(status: SourceEntry.Status): List<Attribute> = Writer()
        .apply { writeStatus(status) }
        .rows

    /** The `preferences` attributes alone, for a settings change that leaves the rest alone. */
    fun preferencesAttributesOf(preferences: SourceEntry.Preferences): List<Attribute> = Writer()
        .apply { writePreferences(preferences) }
        .rows

    /** The `location` attributes alone, for a tombstone, which carries nothing else variant. */
    fun locationAttributesOf(location: SourceLocation.Persistable): List<Attribute> = Writer()
        .apply { writeLocation(location) }
        .rows

    /** The `mode` attributes alone, for a parked request, which carries nothing else variant. */
    fun modeAttributesOf(mode: SyncMode): List<Attribute> = Writer()
        .apply { writeMode(mode) }
        .rows

    // ---------------- reading ----------------

    /**
     * Rebuilds a source, or null when the stored attributes do not describe the variant the row's
     * discriminator names. The caller logs and skips - a half-built source is worse than none.
     */
    fun sourceEntryOf(
        id: String,
        deviceId: String,
        role: String,
        label: String,
        originPath: String,
        createdAtEpochMs: Long,
        lastSyncedAtEpochMs: Long?,
        location: String,
        mode: String,
        status: String,
        attributes: Reader,
    ): SourceEntry? {
        val readRole = SourceEntry.Role.entries.firstOrNull { it.name == role }
            ?: return missing(id, "role '$role' is not one this build knows")

        return SourceEntry(
            id = id,
            deviceId = deviceId,
            location = locationOf(id, location, attributes) ?: return null,
            originPath = originPath,
            syncMode = readMode(id, mode, attributes) ?: return null,
            preferences = readPreferences(id, attributes) ?: return null,
            role = readRole,
            status = readStatus(id, status, attributes) ?: return null,
            label = label,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
            lastSyncedAt = lastSyncedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
    }

    /** Rebuilds a tombstone's location, or null when it cannot be read. */
    fun locationOf(
        sourceId: String,
        location: String,
        attributes: Reader
    ): SourceLocation.Persistable? =
        readLocation(sourceId, location, attributes)

    /** Rebuilds a parked request's mode, or null when it cannot be read. */
    fun modeOf(sourceId: String, mode: String, attributes: Reader): SyncMode? =
        readMode(sourceId, mode, attributes)

    fun tombstoneOf(
        sourceId: String,
        deviceId: String,
        removedAtEpochMs: Long,
        location: SourceLocation.Persistable?,
    ) = SourceTombstone(
        sourceId = sourceId,
        deviceId = deviceId,
        removedAt = Instant.fromEpochMilliseconds(removedAtEpochMs),
        location = location,
    )

    // ---------------- per-part writers ----------------

    private fun Writer.writeLocation(location: SourceLocation.Persistable) {
        when (location) {
            is SourceLocation.Tree -> put(Location, Path, location.path)
            SourceLocation.Media -> Unit
            is SourceLocation.Directory -> put(Location, Path, location.path)
            is SourceLocation.Internal -> put(Location, Bucket, location.bucket)
        }
    }

    private fun Writer.writeMode(mode: SyncMode) {
        when (mode) {
            is SyncMode.Mirror -> put(
                Mode, ConflictResolution, when (mode.conflictResolution) {
                    SyncMode.Mirror.ConflictResolution.LastWriteWins -> LastWriteWins
                    SyncMode.Mirror.ConflictResolution.KeepBoth -> KeepBoth
                }
            )

            // Absent rather than empty when null: a row that exists must carry a readable value.
            is SyncMode.AutoUpload -> put(Mode, IgnoreFilesBefore, mode.ignoreFilesBefore)

            is SyncMode.Offload -> {
                put(Mode, KeepPinned, mode.keepPinned)

                when (val policy = mode.policy) {
                    is SyncMode.Offload.EvictPolicy.OlderThanDays -> {
                        put(Mode, Policy, OlderThanDays)
                        put(Mode, PolicyDays, policy.days.toString())
                    }

                    is SyncMode.Offload.EvictPolicy.LargerThanBytes -> {
                        put(Mode, Policy, LargerThanBytes)
                        put(Mode, PolicyBytes, policy.bytes.toString())
                    }
                }
            }
        }
    }

    private fun Writer.writeStatus(status: SourceEntry.Status) {
        when (status) {
            SourceEntry.Status.Pending, SourceEntry.Status.Active -> Unit
            is SourceEntry.Status.Disabled -> put(Status, Reason, status.reason)
        }
    }

    private fun Writer.writePreferences(preferences: SourceEntry.Preferences) {
        put(Preferences, WifiRequired, preferences.deviceConstraints.wifiRequired)
        put(Preferences, ChargingRequired, preferences.deviceConstraints.chargingRequired)
        preferences.fileLimits.maxFiles?.let { put(Preferences, MaxFiles, it.toString()) }
        preferences.fileLimits.maxTotalSize?.let {
            put(Preferences, MaxTotalBytes, it.bytes.toString())
        }
    }

    // ---------------- per-part readers ----------------

    private fun readLocation(
        id: String,
        discriminator: String,
        attributes: Reader,
    ): SourceLocation.Persistable? = when (discriminator) {
        Tree -> attributes.string(Location, Path)
            ?.let(SourceLocation::Tree)
            ?: missing(id, "location '$Tree' has no '$Path'")

        Media -> SourceLocation.Media

        Directory -> attributes.string(Location, Path)
            ?.let(SourceLocation::Directory)
            ?: missing(id, "location '$Directory' has no '$Path'")

        Internal -> attributes.string(Location, Bucket)
            ?.let(SourceLocation::Internal)
            ?: missing(id, "location '$Internal' has no '$Bucket'")

        else -> missing(id, "location '$discriminator' is not one this build knows")
    }

    private fun readMode(id: String, discriminator: String, attributes: Reader): SyncMode? =
        when (discriminator) {
            Mirror -> when (val stored = attributes.string(Mode, ConflictResolution)) {
                LastWriteWins -> SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins)
                KeepBoth -> SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.KeepBoth)
                null -> missing(id, "mode '$Mirror' has no '$ConflictResolution'")
                else -> missing(id, "conflict resolution '$stored' is not one this build knows")
            }

            // The only optional field in the whole record: absent means "no cutoff", not "broken".
            AutoUpload -> SyncMode.AutoUpload(
                ignoreFilesBefore = attributes.instant(Mode, IgnoreFilesBefore),
            )

            Offload -> {
                val keepPinned = attributes.boolean(Mode, KeepPinned)
                val policy = readPolicy(id, attributes)

                if (keepPinned == null) missing(id, "mode '$Offload' has no '$KeepPinned'")
                else if (policy == null) null
                else SyncMode.Offload(policy = policy, keepPinned = keepPinned)
            }

            else -> missing(id, "mode '$discriminator' is not one this build knows")
        }

    private fun readPolicy(id: String, attributes: Reader): SyncMode.Offload.EvictPolicy? =
        when (val discriminator = attributes.string(Mode, Policy)) {
            OlderThanDays -> attributes.int(Mode, PolicyDays)
                ?.let(SyncMode.Offload.EvictPolicy::OlderThanDays)
                ?: missing(id, "policy '$OlderThanDays' has no readable '$PolicyDays'")

            LargerThanBytes -> attributes.long(Mode, PolicyBytes)
                ?.let(SyncMode.Offload.EvictPolicy::LargerThanBytes)
                ?: missing(id, "policy '$LargerThanBytes' has no readable '$PolicyBytes'")

            null -> missing(id, "mode '$Offload' has no '$Policy'")

            else -> missing(id, "policy '$discriminator' is not one this build knows")
        }

    private fun readStatus(
        id: String,
        discriminator: String,
        attributes: Reader,
    ): SourceEntry.Status? = when (discriminator) {
        Pending -> SourceEntry.Status.Pending

        Active -> SourceEntry.Status.Active

        Disabled -> attributes.string(Status, Reason)
            ?.let(SourceEntry.Status::Disabled)
            ?: missing(id, "status '$Disabled' has no '$Reason'")

        else -> missing(id, "status '$discriminator' is not one this build knows")
    }

    private fun readPreferences(id: String, attributes: Reader): SourceEntry.Preferences? {
        val wifiRequired = attributes.boolean(Preferences, WifiRequired)
            ?: return missing(id, "preferences have no '$WifiRequired'")
        val chargingRequired = attributes.boolean(Preferences, ChargingRequired)
            ?: return missing(id, "preferences have no '$ChargingRequired'")

        // Limits are optional: absent means "no cap". A present value that will not parse is not.
        val maxFiles = attributes.string(Preferences, MaxFiles)?.let {
            it.toIntOrNull()?.takeIf { v -> v >= 0 }
                ?: return missing(id, "preferences have unreadable '$MaxFiles'")
        }
        val maxTotalSize = attributes.string(Preferences, MaxTotalBytes)?.let {
            it.toLongOrNull()?.takeIf { v -> v >= 0 }?.let(::FileSize)
                ?: return missing(id, "preferences have unreadable '$MaxTotalBytes'")
        }

        return SourceEntry.Preferences(
            deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
                wifiRequired = wifiRequired,
                chargingRequired = chargingRequired,
            ),
            fileLimits = SourceEntry.Preferences.FileLimits(
                maxFiles = maxFiles,
                maxTotalSize = maxTotalSize,
            ),
        )
    }

    private fun <T> missing(id: String, what: String): T? {
        Timber.w("Skipping source $id: $what")
        return null
    }

    /** One attribute row, without the source id the caller already has. */
    data class Attribute(val type: String, val field: String, val value: String)

    /**
     * Reads values back out. Every encoding a writer applies is undone here and nowhere else.
     *
     * A value that will not parse reads as absent rather than throwing: it reaches the caller as a
     * refused source, which is the same outcome as a missing row and is handled in one place.
     */
    class Reader(private val rows: Map<Pair<String, String>, String>) {
        fun string(type: String, field: String): String? = rows[type to field]

        fun int(type: String, field: String): Int? = string(type, field)?.toIntOrNull()

        fun long(type: String, field: String): Long? = string(type, field)?.toLongOrNull()

        fun boolean(type: String, field: String): Boolean? = when (string(type, field)) {
            "1" -> true
            "0" -> false
            else -> null
        }

        fun instant(type: String, field: String): Instant? =
            long(type, field)?.let(Instant::fromEpochMilliseconds)
    }

    private class Writer {
        val rows = mutableListOf<Attribute>()

        fun put(type: String, field: String, value: String) {
            rows += Attribute(type, field, value)
        }

        fun put(type: String, field: String, value: Boolean) =
            put(type, field, if (value) "1" else "0")

        /** A null writes nothing: absence is how an optional field is stored. */
        fun put(type: String, field: String, value: Instant?) {
            value?.let { put(type, field, it.toEpochMilliseconds().toString()) }
        }
    }
}
