package com.fserver.app.presentation.screens.source.shared.model

import androidx.annotation.StringRes
import com.fserver.app.R
import kotlinx.serialization.Serializable

/**
 * What happens to a source's files. One mode per source.
 *
 * [Host] is never offered yet: the engine has no `SyncMode` for serving a folder.
 */
@Serializable
enum class SourceModeUi { Sync, AutoUpload, Offload, Host }

@get:StringRes
val SourceModeUi.titleRes: Int
    get() = when (this) {
        SourceModeUi.Sync -> R.string.mode_sync_title
        SourceModeUi.AutoUpload -> R.string.mode_autoupload_title
        SourceModeUi.Offload -> R.string.mode_offload_title
        SourceModeUi.Host -> R.string.mode_host_title
    }
