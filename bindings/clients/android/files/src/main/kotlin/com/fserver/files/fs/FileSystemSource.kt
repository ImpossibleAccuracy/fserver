package com.fserver.files.fs

sealed interface FileSystemSource {
    /** Whole storage volumes. Counterpart to [Tree], which is scoped to one directory. */
    data class Root(val volumes: List<Volume>) : FileSystemSource {
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

    data class Tree(val path: String) : FileSystemSource

    /** One directory on a storage volume, addressed by path. Counterpart to [Tree] without SAF. */
    data class Directory(val path: String) : FileSystemSource

    data object Media : FileSystemSource

    /**
     * App-private storage, scoped to one [bucket] directory under it.
     *
     * One bucket per hosted source, so two peers storing a same-named file here stay apart.
     */
    data class Internal(val bucket: String) : FileSystemSource {
        init {
            require(bucket.isNotBlank()) { "Bucket cannot be blank" }
            require(bucket.none { it == '/' || it == '\\' } && bucket != "." && bucket != "..") {
                "Bucket must be a single directory name: $bucket"
            }
        }
    }
}
