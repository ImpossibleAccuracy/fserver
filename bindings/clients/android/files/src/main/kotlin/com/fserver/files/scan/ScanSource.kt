package com.fserver.files.scan

sealed interface ScanSource {
    data class Root(val rootPaths: List<String>) : ScanSource {
        init {
            require(rootPaths.isNotEmpty()) { "Root paths list cannot be empty" }
        }
    }

    data class Tree(val path: String) : ScanSource

    data object Media : ScanSource
}