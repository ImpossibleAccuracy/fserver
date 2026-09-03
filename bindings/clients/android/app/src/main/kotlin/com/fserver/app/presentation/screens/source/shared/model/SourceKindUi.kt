package com.fserver.app.presentation.screens.source.shared.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.R
import kotlinx.serialization.Serializable

@Serializable
enum class SourceKindUi {
    Media,
    Folder,
    WholeDevice,

    /**
     * The app's own private storage.
     *
     * TODO: remove before production. It needs no grant, so it is the branch that lets the whole
     *  send flow be walked on a device where nothing was ever shared.
     */
    AppStorage,
}

val SourceKindUi.icon: ImageVector
    get() = when (this) {
        SourceKindUi.Media -> Icons.Default.PhotoLibrary
        SourceKindUi.Folder -> Icons.Default.Folder
        SourceKindUi.WholeDevice -> Icons.Default.PhoneAndroid
        SourceKindUi.AppStorage -> Icons.Default.Science
    }

@get:StringRes
val SourceKindUi.titleRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_photos_title
        SourceKindUi.Folder -> R.string.source_folder_title
        SourceKindUi.WholeDevice -> R.string.source_device_title
        SourceKindUi.AppStorage -> R.string.source_appstorage_title
    }

@get:StringRes
val SourceKindUi.subtitleRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_photos_subtitle
        SourceKindUi.Folder -> R.string.source_folder_subtitle
        SourceKindUi.WholeDevice -> R.string.source_device_subtitle
        SourceKindUi.AppStorage -> R.string.source_appstorage_subtitle
    }

/** Title of every screen that belongs to the branch, so the user keeps seeing where they are. */
@get:StringRes
val SourceKindUi.modeTitleRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_mode_photos_title
        SourceKindUi.Folder -> R.string.source_mode_folder_title
        SourceKindUi.WholeDevice -> R.string.source_mode_device_title
        SourceKindUi.AppStorage -> R.string.source_mode_appstorage_title
    }
