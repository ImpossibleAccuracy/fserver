package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.R
import kotlinx.serialization.Serializable

/**
 * The send flow, in presentation terms.
 *
 * Screen 0 asks a single question — what the app is allowed to see on this phone ([SourceKindUi]).
 * Only once access is actually granted does the branch ask what to do with it ([SourceModeUi]),
 * and only then where it goes. Nothing here reads or moves a byte; `:core` is not wired in yet.
 */
@Serializable
enum class SourceKindUi { Photos, Folder, WholeDevice }

/**
 * What happens to a source's files. One mode per source.
 *
 * [Offload] is the only one that removes originals from the phone, so it carries a warning
 * wherever it is listed and gets an explainer of its own before it can be turned on.
 */
@Serializable
enum class SourceModeUi { Sync, AutoUpload, Offload, Host }

/**
 * How much of a source Android actually handed over. Partial is a lasting state rather than an
 * event — the photos branch keeps saying so at the top of every screen after it.
 */
@Serializable
enum class SourceAccessUi { Full, Partial }

/** Which files auto-upload picks up: only what arrives from now on, or the backlog too. */
@Serializable
enum class UploadScopeUi { New, All }

/** The rule that decides what offload evicts first. */
@Serializable
enum class EvictCriterionUi { OlderThanDays, LeastRecentlyUsed }

/** What a hosted folder lets trusted devices do. */
@Serializable
enum class HostRightsUi { ReadOnly, ReadWrite }

val SourceKindUi.icon: ImageVector
    get() = when (this) {
        SourceKindUi.Photos -> Icons.Default.PhotoLibrary
        SourceKindUi.Folder -> Icons.Default.Folder
        SourceKindUi.WholeDevice -> Icons.Default.PhoneAndroid
    }

@get:StringRes
val SourceKindUi.titleRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_photos_title
        SourceKindUi.Folder -> R.string.source_folder_title
        SourceKindUi.WholeDevice -> R.string.source_device_title
    }

@get:StringRes
val SourceKindUi.subtitleRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_photos_subtitle
        SourceKindUi.Folder -> R.string.source_folder_subtitle
        SourceKindUi.WholeDevice -> R.string.source_device_subtitle
    }

/** Title of every screen that belongs to the branch, so the user keeps seeing where they are. */
@get:StringRes
val SourceKindUi.modeTitleRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_mode_photos_title
        SourceKindUi.Folder -> R.string.source_mode_folder_title
        SourceKindUi.WholeDevice -> R.string.source_mode_device_title
    }

/**
 * Modes the branch may offer. Photos has no folder to hand out or mirror, so it is the short
 * list; the other two share the full one, ordered from the safest to the most irreversible.
 */
val SourceKindUi.modes: List<SourceModeUi>
    get() = when (this) {
        SourceKindUi.Photos -> listOf(SourceModeUi.AutoUpload, SourceModeUi.Offload)
        SourceKindUi.Folder,
        SourceKindUi.WholeDevice -> listOf(
            SourceModeUi.Sync,
            SourceModeUi.AutoUpload,
            SourceModeUi.Offload,
            SourceModeUi.Host,
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
