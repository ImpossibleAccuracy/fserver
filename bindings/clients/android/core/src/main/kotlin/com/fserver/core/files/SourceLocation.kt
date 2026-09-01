package com.fserver.core.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager

sealed interface SourceLocation {
    data class Root(val rootPaths: List<String>) : SourceLocation {
        init {
            require(rootPaths.isNotEmpty()) { "Root paths list cannot be empty" }
        }

        companion object {
            /** Every storage volume the OS will hand out. */
            fun fromContext(context: Context): Root {
                val sm = context.getSystemService(StorageManager::class.java)
                val roots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sm.storageVolumes.mapNotNull { it.directory?.absolutePath }
                } else {
                    listOfNotNull(Environment.getExternalStorageDirectory().absolutePath)
                }

                return Root(roots)
            }
        }
    }

    data class Tree(val path: String) : SourceLocation

    data object Media : SourceLocation
}