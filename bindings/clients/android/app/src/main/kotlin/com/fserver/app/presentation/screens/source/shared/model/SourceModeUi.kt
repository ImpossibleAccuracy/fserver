package com.fserver.app.presentation.screens.source.shared.model

import androidx.annotation.StringRes
import com.fserver.app.R
import kotlinx.serialization.Serializable

/** What happens to a source's files. One mode per source. */
@Serializable
enum class SourceModeUi { Sync, AutoUpload, Offload, Host }

/**
 * Modes the branch may offer, ordered from the safest to the most irreversible. Photos has no
 * folder to mirror, so it is the short list.
 *
 * [SourceModeUi.Host] is offered by neither: the engine has no `SyncMode` for serving a folder
 * yet, so a source registered under it could not run. The copy stays for when it does.
 */
val SourceKindUi.modes: List<SourceModeUi>
    get() = when (this) {
        SourceKindUi.Media -> listOf(SourceModeUi.AutoUpload, SourceModeUi.Offload)
        SourceKindUi.Folder,
        SourceKindUi.WholeDevice -> listOf(
            SourceModeUi.Sync,
            SourceModeUi.AutoUpload,
            SourceModeUi.Offload,
        )
    }

@get:StringRes
val SourceModeUi.titleRes: Int
    get() = when (this) {
        SourceModeUi.Sync -> R.string.mode_sync_title
        SourceModeUi.AutoUpload -> R.string.mode_autoupload_title
        SourceModeUi.Offload -> R.string.mode_offload_title
        SourceModeUi.Host -> R.string.mode_host_title
    }
