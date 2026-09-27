package com.fserver.app.presentation.screens.source.shared.preferences.model

import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class SourcePreferencesUiTest {

    @Test
    fun `follower only gets constraints and limits`() {
        SourceModeUi.entries.forEach { mode ->
            val ui = SourcePreferencesUi.build(mode, SourceRoleUi.Follower)

            assertNotNull(ui.limits)
            assertNull(ui.conflicts)
            assertNull(ui.upload)
            assertNull(ui.eviction)
        }
    }

    @Test
    fun `initiator gets the mode's own section`() {
        val sync = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Initiator)
        assertNotNull(sync.conflicts)
        assertNotNull(sync.limits)

        val upload = SourcePreferencesUi.build(SourceModeUi.AutoUpload, SourceRoleUi.Initiator)
        assertNotNull(upload.upload)
        assertNull(upload.limits)

        val offload = SourcePreferencesUi.build(SourceModeUi.Offload, SourceRoleUi.Initiator)
        assertNotNull(offload.eviction)
        assertNull(offload.limits)
    }

    @Test
    fun `an existing source reads back what was saved`() {
        val preferences = SourceEntry.Preferences(
            deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
                wifiRequired = false,
                chargingRequired = true,
            ),
            fileLimits = SourceEntry.Preferences.FileLimits(
                maxFiles = 300,
                maxTotalSize = FileSize(7 * SourcePreferencesUi.BytesInGb),
            ),
        )
        val source = entry(
            mode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
            preferences = preferences,
        )

        val ui = SourcePreferencesUi.build(source)

        assertEquals(ConflictResolutionUi.LastWriteWins, ui.conflicts?.resolution)
        assertEquals(true, ui.limits?.customSize)
        assertEquals(preferences, ui.toPreferences())
        assertEquals(source.syncMode, ui.toSyncMode(SourceModeUi.Sync))
    }

    @Test
    fun `every offload policy reads back as saved`() {
        val policies = listOf(
            SyncMode.Offload.EvictPolicy.OlderThanDays(30),
            SyncMode.Offload.EvictPolicy.LargerThanBytes(200 * SourcePreferencesUi.BytesInMb),
        )

        for (policy in policies) {
            val source = entry(mode = SyncMode.Offload(policy), preferences = SourceEntry.Preferences.Default)

            assertEquals(source.syncMode, SourcePreferencesUi.build(source).toSyncMode(SourceModeUi.Offload))
        }
    }

    @Test
    fun `host has no mode settings of its own and maps to its mode`() {
        val ui = SourcePreferencesUi.build(SourceModeUi.Host, SourceRoleUi.Initiator)

        assertNull(ui.eviction)
        assertNull(ui.limits)
        assertEquals(SyncMode.Host, ui.toSyncMode(SourceModeUi.Host))
    }

    @Test
    fun `size preset and custom size are exclusive`() {
        val base = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Follower)

        val custom = base.reduce(SourcePreferencesIntent.SizePresetSelected(null))
        assertNull(custom.limits?.sizePresetGb)

        val preset = custom.reduce(SourcePreferencesIntent.SizePresetSelected(10))
        assertEquals(10, preset.limits?.sizePresetGb)
    }

    private fun entry(mode: SyncMode, preferences: SourceEntry.Preferences) = SourceEntry(
        id = "s",
        deviceId = "d",
        location = SourceLocation.Internal(bucket = "s"),
        originPath = "/",
        syncMode = mode,
        preferences = preferences,
        role = SourceEntry.Role.Initiator,
        status = SourceEntry.Status.Active,
        label = "s",
        createdAt = Instant.fromEpochMilliseconds(0),
    )
}
