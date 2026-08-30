package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.mode.model.SourceModeState
import com.fserver.app.presentation.screens.source.shared.composable.EvictCriterionUi
import com.fserver.app.presentation.screens.source.shared.composable.HostRightsUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.composable.UploadScopeUi
import com.fserver.app.presentation.screens.source.shared.composable.modes

data class SourceFlowState(
    val kind: SourceKindUi? = null,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val moreExpanded: Boolean = false,
    val accessPhase: SourceAccessState.Phase = SourceAccessState.Phase.Explaining,
    val scanPath: String = "",
    val scannedFiles: Int = 0,
    val scannedBytes: Long = 0,
    val source: PickedSourceUi? = null,
    val mode: SourceModeUi? = null,
    val explainerAccepted: Boolean = false,
    val preparing: Boolean = false,
    val uploadScope: UploadScopeUi = UploadScopeUi.New,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
    val olderThanDays: Int = SourceConditionsState.DefaultDays,
    val keepPinned: Boolean = true,
    val hostRights: HostRightsUi = HostRightsUi.ReadOnly,
    val prepareProgress: Float = 0f,
    val prepareDetail: String = "",
) {
    val resolvedMode: SourceModeUi?
        get() = mode ?: kind?.modes?.firstOrNull()

    val sourceLabel: String
        get() = source?.label?.ifEmpty { null } ?: kind.fallbackLabel()

    fun toAccessState(): SourceAccessState? = SourceAccessState(
        kind = kind ?: return null,
        phase = accessPhase,
        access = access,
        scanPath = scanPath,
        scannedFiles = scannedFiles,
        scannedBytes = scannedBytes,
    )

    fun toModeState(): SourceModeState? = SourceModeState(
        kind = kind ?: return null,
        access = access,
        selected = resolvedMode,
        grantedItemCount = if (access == SourceAccessUi.Partial) source?.files ?: 0 else 0,
        sourceLabel = if (kind == SourceKindUi.Media) "" else sourceLabel,
        sourceFiles = source?.files ?: 0,
        sourceBytes = source?.bytes ?: 0,
    )

    fun toConditionsState(): SourceConditionsState? = SourceConditionsState(
        kind = kind ?: return null,
        mode = resolvedMode ?: return null,
        phase = when {
            preparing -> SourceConditionsState.Phase.Preparing
            resolvedMode == SourceModeUi.Offload && !explainerAccepted ->
                SourceConditionsState.Phase.Explainer

            else -> SourceConditionsState.Phase.Form
        },
        targetName = SampleTarget,
        sourceLabel = if (kind == SourceKindUi.Media) "" else sourceLabel,
        uploadScope = uploadScope,
        backlogLabel = SampleBacklog,
        wifiOnly = wifiOnly,
        chargingOnly = chargingOnly,
        criterion = criterion,
        olderThanDays = olderThanDays,
        keepPinned = keepPinned,
        hostRights = hostRights,
        progress = prepareProgress,
        progressDetail = prepareDetail,
    )

    fun toSummary(): SourceSummaryUi? = SourceSummaryUi(
        kind = kind ?: return null,
        mode = resolvedMode ?: return null,
        sourceLabel = sourceLabel,
        files = source?.files ?: scannedFiles,
        bytes = source?.bytes ?: scannedBytes,
        olderThanDays = olderThanDays,
    )

    private companion object {
        const val SampleTarget = "HOME-NAS"
        const val SampleFolder = "DCIM/Projects"
        const val SampleBacklog = "3,402"

        fun SourceKindUi?.fallbackLabel(): String = when (this) {
            SourceKindUi.Media -> "Photos and videos"
            SourceKindUi.Folder -> SampleFolder
            SourceKindUi.WholeDevice -> "Whole device"
            null -> ""
        }
    }
}