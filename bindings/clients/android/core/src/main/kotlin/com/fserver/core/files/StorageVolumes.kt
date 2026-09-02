package com.fserver.core.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.fserver.common.utils.SourcePaths

object StorageVolumes {
    /** Every storage volume the OS will hand out, as one scannable root. */
    fun fromContext(context: Context): SourceLocation.Root {
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

                SourceLocation.Root.Volume(id = id, path = directory.absolutePath)
            }
        } else {
            listOf(
                SourceLocation.Root.Volume(
                    id = SourcePaths.PrimaryVolume,
                    path = Environment.getExternalStorageDirectory().absolutePath,
                )
            )
        }

        return SourceLocation.Root(volumes)
    }
}
