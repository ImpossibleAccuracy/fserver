package com.fserver.core.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.fserver.common.utils.SourcePaths

sealed interface SourceLocation {
    /** The locations a user may point a source at. */
    sealed interface Selectable : SourceLocation

    /** Where files a peer sends may be written */
    sealed interface Hostable : SourceLocation

    /** Whole storage volumes. Counterpart to [Tree], which is scoped to one directory. */
    data class Root(val volumes: List<Volume>) : Selectable {
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

    /** One directory, addressed by the Storage Access Framework tree the user granted. */
    data class Tree(val path: String) : Selectable, Hostable

    data object Media : Selectable

    /**
     * One directory on a storage volume, addressed by path - [Root] scoped down to a single
     * folder, and only reachable with a grant wide enough to write outside the app's own storage.
     */
    data class Directory(val path: String) : Hostable

    /**
     * App-private storage, scoped to one [bucket] directory under it.
     * The default place a hosted source lands.
     */
    data class Internal(val bucket: String) : Hostable {
        init {
            require(bucket.isNotBlank()) { "Bucket cannot be blank" }
            require(bucket.none { it == '/' || it == '\\' } && bucket != "." && bucket != "..") {
                "Bucket must be a single directory name: $bucket"
            }
        }
    }
}
