package com.fserver.core.storage.internal

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/**
 * The attribute rows are untyped, so this mapper is the only thing standing between a stored source
 * and a wrong one. Two failures it has to keep out: a writer and a reader that spell a field
 * differently, and a read that fills in what the rows do not actually say.
 */
class SourceRecordsTest {

    @Test
    fun `every variant survives a round trip`() {
        val locations = listOf(
            SourceLocation.Tree("content://tree/primary%3ADocs"),
            SourceLocation.Media,
            SourceLocation.Directory("/storage/emulated/0/Pictures"),
            SourceLocation.Internal("inbox"),
        )

        val modes = listOf(
            SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
            SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.KeepBoth),
            SyncMode.AutoUpload(ignoreFilesBefore = null),
            SyncMode.AutoUpload(ignoreFilesBefore = Instant.fromEpochMilliseconds(1_700_000_000_000)),
            SyncMode.Offload(
                policy = SyncMode.Offload.EvictPolicy.OlderThanDays(30),
                keepPinned = true,
            ),
            SyncMode.Offload(
                policy = SyncMode.Offload.EvictPolicy.LargerThanBytes(512L * 1024 * 1024),
                keepPinned = false,
            ),
        )

        val statuses = listOf(
            SourceEntry.Status.Pending,
            SourceEntry.Status.Active,
            SourceEntry.Status.Disabled("peer dropped its half"),
        )

        for (location in locations) {
            for (mode in modes) {
                for (status in statuses) {
                    val source = source(location = location, syncMode = mode, status = status)

                    assertEquals("$location / $mode / $status", source, roundTrip(source))
                }
            }
        }
    }

    @Test
    fun `role and timestamps survive a round trip`() {
        val source = source(
            role = SourceEntry.Role.Follower,
            createdAt = Instant.fromEpochMilliseconds(1_600_000_000_000),
            lastSyncedAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        )

        assertEquals(source, roundTrip(source))
    }

    @Test
    fun `preferences survive a round trip, limits set or not`() {
        val preferences = listOf(
            SourceEntry.Preferences.Default,
            SourceEntry.Preferences(
                deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
                    wifiRequired = true,
                    chargingRequired = true,
                ),
                fileLimits = SourceEntry.Preferences.FileLimits(
                    maxFiles = 1000,
                    maxTotalSize = FileSize(4L * 1024 * 1024 * 1024),
                ),
            ),
        )

        for (prefs in preferences) {
            val source = source(preferences = prefs)

            assertEquals(source, roundTrip(source))
        }
    }

    @Test
    fun `a source whose attributes are gone is refused, not defaulted`() {
        val source = source(
            location = SourceLocation.Internal("inbox"),
            syncMode = SyncMode.Offload(
                policy = SyncMode.Offload.EvictPolicy.OlderThanDays(30),
                keepPinned = true,
            ),
        )

        // What a renamed field leaves behind: the row still names the variant, its values do not.
        assertNull(read(source, attributes = emptyList()))
    }

    @Test
    fun `a partly written variant is refused`() {
        val source = source(
            syncMode = SyncMode.Offload(
                policy = SyncMode.Offload.EvictPolicy.LargerThanBytes(1024),
                keepPinned = true,
            ),
        )

        val withoutPolicyValue = SourceRecords.attributesOf(source)
            .filterNot { it.field == "policy.bytes" }

        assertNull(read(source, withoutPolicyValue))
    }

    @Test
    fun `a discriminator this build does not know is refused`() {
        val source = source()

        assertNull(read(source, SourceRecords.attributesOf(source), mode = "DeltaSync"))
    }

    @Test
    fun `a value that will not parse is refused`() {
        val source = source(
            syncMode = SyncMode.Offload(
                policy = SyncMode.Offload.EvictPolicy.OlderThanDays(30),
                keepPinned = true,
            ),
        )

        val corrupted = SourceRecords.attributesOf(source).map { attribute ->
            if (attribute.field == "policy.days") attribute.copy(value = "thirty") else attribute
        }

        assertNull(read(source, corrupted))
    }

    private fun roundTrip(source: SourceEntry): SourceEntry? =
        read(source, SourceRecords.attributesOf(source))

    /** Writes [source] the way the store would, then reads it back off [attributes] alone. */
    private fun read(
        source: SourceEntry,
        attributes: List<SourceRecords.Attribute>,
        mode: String = SourceRecords.discriminatorOf(source.syncMode),
    ): SourceEntry? = SourceRecords.sourceEntryOf(
        id = source.id,
        deviceId = source.deviceId,
        role = source.role.name,
        label = source.label,
        originPath = source.originPath,
        createdAtEpochMs = source.createdAt.toEpochMilliseconds(),
        lastSyncedAtEpochMs = source.lastSyncedAt?.toEpochMilliseconds(),
        location = SourceRecords.discriminatorOf(source.location),
        mode = mode,
        status = SourceRecords.discriminatorOf(source.status),
        attributes = SourceRecords.Reader(
            attributes.associate { (it.type to it.field) to it.value }
        ),
    )

    private fun source(
        location: SourceLocation.Persistable = SourceLocation.Media,
        syncMode: SyncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
        status: SourceEntry.Status = SourceEntry.Status.Active,
        role: SourceEntry.Role = SourceEntry.Role.Initiator,
        createdAt: Instant = Instant.fromEpochMilliseconds(1_600_000_000_000),
        lastSyncedAt: Instant? = null,
        preferences: SourceEntry.Preferences = SourceEntry.Preferences.Default,
    ) = SourceEntry(
        id = "source-1",
        deviceId = "device-1",
        location = location,
        originPath = "/Camera",
        syncMode = syncMode,
        preferences = preferences,
        role = role,
        status = status,
        label = "Camera",
        createdAt = createdAt,
        lastSyncedAt = lastSyncedAt,
    )
}
