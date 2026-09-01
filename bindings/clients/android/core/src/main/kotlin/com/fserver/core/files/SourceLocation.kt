package com.fserver.core.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.fserver.common.utils.SourcePaths

sealed interface SourceLocation {
    /** Whole storage volumes. Counterpart to [Tree], which is scoped to one directory. */
    data class Root(val volumes: List<Volume>) : SourceLocation {
        init {
            require(volumes.isNotEmpty()) { "Volumes list cannot be empty" }
        }

        /**
         * One storage volume. [id] leads every path found on it, so a file on internal storage
         * never collides with a same-named one on an SD card.
         */
        data class Volume(
            /** [SourcePaths.PrimaryVolume], or the removable volume's uuid. */
            val id: String,
            /** Mount point on this device. Local only - a peer mounts it somewhere else. */
            val path: String,
        )

        companion object {
            /** Every storage volume the OS will hand out. */
            fun fromContext(context: Context): Root {
                val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val sm = context.getSystemService(StorageManager::class.java)

                    sm.storageVolumes.mapNotNull { volume ->
                        val directory = volume.directory ?: return@mapNotNull null
                        // A volume with neither flag has no stable id, so nothing found on it could
                        // be matched against a peer. Skipping beats emitting paths that never join.
                        val id = if (volume.isPrimary) {
                            SourcePaths.PrimaryVolume
                        } else {
                            volume.uuid ?: return@mapNotNull null
                        }

                        Volume(id = id, path = directory.absolutePath)
                    }
                } else {
                    listOf(
                        Volume(
                            id = SourcePaths.PrimaryVolume,
                            path = Environment.getExternalStorageDirectory().absolutePath,
                        )
                    )
                }

                return Root(volumes)
            }
        }
    }

    data class Tree(val path: String) : SourceLocation

    data object Media : SourceLocation
}
