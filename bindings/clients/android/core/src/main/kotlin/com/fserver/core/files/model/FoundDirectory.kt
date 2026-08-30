package com.fserver.core.files.model

import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager

sealed interface FoundDirectory {
    data class Root(val rootPaths: List<String>) : FoundDirectory {
        init {
            require(rootPaths.isNotEmpty()) { "Root paths list cannot be empty" }
        }

        companion object {
            fun fromContext(context: android.content.Context): Root {
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

    data class Tree(val path: String) : FoundDirectory

    data object Media : FoundDirectory
}
