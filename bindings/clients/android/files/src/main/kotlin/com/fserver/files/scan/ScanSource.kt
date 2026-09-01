package com.fserver.files.scan

sealed interface ScanSource {
    /** Whole storage volumes. Counterpart to [Tree], which is scoped to one directory. */
    data class Root(val volumes: List<Volume>) : ScanSource {
        init {
            require(volumes.isNotEmpty()) { "Volumes list cannot be empty" }
        }

        /**
         * One storage volume. [id] leads every path found on it, so a file on internal storage
         * never collides with a same-named one on an SD card.
         */
        data class Volume(
            /** `SourcePaths.PrimaryVolume`, or the removable volume's uuid. */
            val id: String,
            /** Mount point on this device. Local only. */
            val path: String,
        )
    }

    data class Tree(val path: String) : ScanSource

    data object Media : ScanSource
}
