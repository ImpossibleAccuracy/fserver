package com.fserver.app.presentation.screens.source.shared.preferences.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.DaysStep
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.LargerThanStepMb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxDays
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxFilesStep
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxLargerThanMb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxMaxFiles
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxMaxSizeGb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MaxSizeStepGb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MinDays
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MinLargerThanMb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MinMaxFiles
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.MinMaxSizeGb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.SizePresetsGb
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi.Companion.build
import com.fserver.common.model.FileSize
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Everything a user may tune on one side of a source, shaped by its mode and this device's role.
 *
 * A null section is one that does not apply: the mode's own settings (conflicts, upload scope,
 * eviction) belong to the initiator, which owns the [SyncMode]; the follower only decides when and
 * how much it takes in. Build it with [build] so creating, accepting and editing agree on that.
 */
@Immutable
data class SourcePreferencesUi(
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val limits: LimitsUi? = null,
    val conflicts: ConflictsUi? = null,
    val upload: UploadUi? = null,
    val eviction: EvictionUi? = null,
) {
    @Immutable
    data class LimitsUi(
        val limitFiles: Boolean = false,
        val maxFiles: Int = DefaultMaxFiles,
        val limitSize: Boolean = false,
        val maxSizeGb: Int = DefaultMaxSizeGb,
        /** The size came from the stepper rather than one of [SizePresetsGb]. */
        val customSize: Boolean = false,
    ) {
        val maxSizeBytes: Long get() = maxSizeGb.toLong() * BytesInGb

        /** The choice bar's value: a preset, or null for a custom size. */
        val sizePresetGb: Int? get() = maxSizeGb.takeIf { !customSize && it in SizePresetsGb }
    }

    @Immutable
    data class ConflictsUi(
        val resolution: ConflictResolutionUi = ConflictResolutionUi.Ask,
    )

    @Immutable
    data class UploadUi(val scope: UploadScopeUi = UploadScopeUi.New)

    @Immutable
    data class EvictionUi(
        val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
        val olderThanDays: Int = DefaultDays,
        val largerThanMb: Int = DefaultLargerThanMb,
    ) {
        val largerThanBytes: Long get() = largerThanMb.toLong() * BytesInMb
    }

    companion object {
        const val DefaultMaxFiles = 1000
        const val MaxFilesStep = 100
        const val MinMaxFiles = 100
        const val MaxMaxFiles = 100_000

        const val DefaultMaxSizeGb = 5
        const val MaxSizeStepGb = 1
        const val MinMaxSizeGb = 1
        const val MaxMaxSizeGb = 1024
        val SizePresetsGb = listOf(1, 5, 10)

        const val DefaultDays = 60
        const val DaysStep = 15
        const val MinDays = 15
        const val MaxDays = 365

        const val DefaultLargerThanMb = 100
        const val LargerThanStepMb = 50
        const val MinLargerThanMb = 10
        const val MaxLargerThanMb = 4096

        const val BytesInMb = 1024L * 1024
        const val BytesInGb = 1024L * BytesInMb

        // TODO: build works pretty bad, need to refactor the whole thing

        /** Defaults for a new source of [mode], as seen from [role]. */
        fun build(mode: SourceModeUi, role: SourceRoleUi): SourcePreferencesUi {
            val initiator = role == SourceRoleUi.Initiator

            return SourcePreferencesUi(
                limits = LimitsUi().takeIf { !initiator || mode == SourceModeUi.Sync },
                conflicts = ConflictsUi().takeIf { initiator && mode == SourceModeUi.Sync },
                upload = UploadUi().takeIf { initiator && mode == SourceModeUi.AutoUpload },
                eviction = EvictionUi().takeIf { initiator && mode == SourceModeUi.Offload },
            )
        }

        /** An existing source's settings, for editing it. */
        fun build(source: SourceEntry): SourcePreferencesUi {
            val defaults = build(source.syncMode.toUi(), source.role.toUi())
            val mode = source.syncMode

            return defaults.copy(
                wifiOnly = source.preferences.deviceConstraints.wifiRequired,
                chargingOnly = source.preferences.deviceConstraints.chargingRequired,
                limits = defaults.limits?.let { source.preferences.fileLimits.toUi() },
                conflicts = defaults.conflicts?.let { (mode as? SyncMode.Mirror)?.toUi() ?: it },
                upload = defaults.upload?.let { (mode as? SyncMode.AutoUpload)?.toUi() ?: it },
                eviction = defaults.eviction?.let { (mode as? SyncMode.Offload)?.toUi() ?: it },
            )
        }

        private fun SyncMode.Mirror.toUi() = ConflictsUi(
            resolution = when (conflictResolution) {
                SyncMode.Mirror.ConflictResolution.LastWriteWins -> ConflictResolutionUi.LastWriteWins
                SyncMode.Mirror.ConflictResolution.Ask -> ConflictResolutionUi.Ask
            },
        )

        private fun SyncMode.AutoUpload.toUi() = UploadUi(
            scope = if (ignoreFilesBefore == null) UploadScopeUi.All else UploadScopeUi.New,
        )

        private fun SyncMode.Offload.toUi() = when (val policy = policy) {
            is SyncMode.Offload.EvictPolicy.OlderThanDays -> EvictionUi(
                criterion = EvictCriterionUi.OlderThanDays,
                olderThanDays = policy.days,
            )

            is SyncMode.Offload.EvictPolicy.LargerThanBytes -> EvictionUi(
                criterion = EvictCriterionUi.LargerThan,
                largerThanMb = (policy.bytes / BytesInMb).toInt()
                    .coerceIn(MinLargerThanMb, MaxLargerThanMb),
            )
        }

        private fun SourceEntry.Preferences.FileLimits.toUi(): LimitsUi {
            val sizeGb =
                maxTotalSize?.let { (it.bytes / BytesInGb).toInt().coerceAtLeast(MinMaxSizeGb) }

            return LimitsUi(
                limitFiles = maxFiles != null,
                maxFiles = maxFiles ?: DefaultMaxFiles,
                limitSize = sizeGb != null,
                maxSizeGb = sizeGb ?: DefaultMaxSizeGb,
                customSize = sizeGb != null && sizeGb !in SizePresetsGb,
            )
        }
    }
}

fun SourcePreferencesUi.reduce(intent: SourcePreferencesIntent): SourcePreferencesUi =
    when (intent) {
        is SourcePreferencesIntent.WifiOnlyToggled -> copy(wifiOnly = intent.enabled)
        is SourcePreferencesIntent.ChargingOnlyToggled -> copy(chargingOnly = intent.enabled)

        is SourcePreferencesIntent.LimitFilesToggled -> updateLimits { copy(limitFiles = intent.enabled) }
        is SourcePreferencesIntent.MaxFilesStepped -> updateLimits {
            copy(maxFiles = stepped(maxFiles, intent.steps, MaxFilesStep, MinMaxFiles, MaxMaxFiles))
        }

        is SourcePreferencesIntent.LimitSizeToggled -> updateLimits { copy(limitSize = intent.enabled) }
        is SourcePreferencesIntent.SizePresetSelected -> updateLimits {
            when (val gb = intent.gb) {
                null -> copy(customSize = true)
                else -> copy(customSize = false, maxSizeGb = gb)
            }
        }

        is SourcePreferencesIntent.MaxSizeStepped -> updateLimits {
            copy(
                maxSizeGb = stepped(
                    maxSizeGb,
                    intent.steps,
                    MaxSizeStepGb,
                    MinMaxSizeGb,
                    MaxMaxSizeGb
                )
            )
        }

        is SourcePreferencesIntent.ConflictResolutionSelected ->
            copy(conflicts = conflicts?.copy(resolution = intent.resolution))

        is SourcePreferencesIntent.UploadScopeSelected ->
            copy(upload = upload?.copy(scope = intent.scope))

        is SourcePreferencesIntent.CriterionSelected ->
            updateEviction { copy(criterion = intent.criterion) }

        is SourcePreferencesIntent.DaysStepped -> updateEviction {
            copy(olderThanDays = stepped(olderThanDays, intent.steps, DaysStep, MinDays, MaxDays))
        }

        is SourcePreferencesIntent.SizeThresholdStepped -> updateEviction {
            copy(
                largerThanMb = stepped(
                    largerThanMb,
                    intent.steps,
                    LargerThanStepMb,
                    MinLargerThanMb,
                    MaxLargerThanMb
                )
            )
        }
    }

fun SourcePreferencesUi.toPreferences() = SourceEntry.Preferences(
    deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
        wifiRequired = wifiOnly,
        chargingRequired = chargingOnly,
    ),
    fileLimits = limits?.let {
        SourceEntry.Preferences.FileLimits(
            maxFiles = it.maxFiles.takeIf { _ -> it.limitFiles },
            maxTotalSize = FileSize(it.maxSizeBytes).takeIf { _ -> it.limitSize },
        )
    } ?: SourceEntry.Preferences.FileLimits.None,
)

/**
 * The initiator's [SyncMode] for [mode]. Auto-upload scoped to new files is a cut-off rather than a
 * filter, which is why the scope becomes an instant.
 */
fun SourcePreferencesUi.toSyncMode(
    mode: SourceModeUi,
    now: Instant = Clock.System.now()
): SyncMode =
    when (mode) {
        SourceModeUi.Sync -> SyncMode.Mirror(
            conflictResolution = when (conflicts?.resolution ?: ConflictResolutionUi.Ask) {
                ConflictResolutionUi.LastWriteWins -> SyncMode.Mirror.ConflictResolution.LastWriteWins
                ConflictResolutionUi.Ask -> SyncMode.Mirror.ConflictResolution.Ask
            },
        )

        SourceModeUi.AutoUpload -> SyncMode.AutoUpload(
            ignoreFilesBefore = when (upload?.scope ?: UploadScopeUi.New) {
                UploadScopeUi.New -> now
                UploadScopeUi.All -> null
            },
        )

        SourceModeUi.Offload -> SyncMode.Offload(
            policy = (eviction ?: SourcePreferencesUi.EvictionUi()).let {
                when (it.criterion) {
                    EvictCriterionUi.OlderThanDays -> SyncMode.Offload.EvictPolicy.OlderThanDays(it.olderThanDays)
                    EvictCriterionUi.LargerThan -> SyncMode.Offload.EvictPolicy.LargerThanBytes(it.largerThanBytes)
                }
            },
        )

        SourceModeUi.Host -> SyncMode.Host
    }

private fun SourcePreferencesUi.updateLimits(
    block: SourcePreferencesUi.LimitsUi.() -> SourcePreferencesUi.LimitsUi,
) = copy(limits = limits?.block())

private fun SourcePreferencesUi.updateEviction(
    block: SourcePreferencesUi.EvictionUi.() -> SourcePreferencesUi.EvictionUi,
) = copy(eviction = eviction?.block())

private fun stepped(current: Int, steps: Int, step: Int, min: Int, max: Int): Int =
    (current + steps * step).coerceIn(min, max)
