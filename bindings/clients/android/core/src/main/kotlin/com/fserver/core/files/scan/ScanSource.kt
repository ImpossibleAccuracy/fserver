package com.fserver.core.files.scan

import android.content.Context

sealed interface ScanSource {
    data class Root(val rootPaths: List<String>) : ScanSource {
        init {
            require(rootPaths.isNotEmpty()) { "Root paths list cannot be empty" }
        }

        companion object {
            /** Every storage volume the OS will hand out, delegated to `:files`. */
            fun fromContext(context: Context): Root =
                Root(com.fserver.files.model.ScanSource.Root.fromContext(context).rootPaths)
        }
    }

    data class Tree(val path: String) : ScanSource

    data object Media : ScanSource
}