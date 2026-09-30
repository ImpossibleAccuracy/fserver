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
     * One [directory] under the public Downloads folder of the primary volume. Any file type, unlike
     * [Media]; no storage permission on Android 10+, where only rows this app wrote are visible.
     */
    data class Downloads(val directory: String) : FileSystemSource {
        init {
            requireSingleSegment(directory)
        }
    }

    /**
     * Files other apps handed over by `content://` uri - a share sheet, a picker. Read-only, and
     * readable only while the grant that came with each uri lasts: for a share sheet, until the
     * receiving task is gone. A scan reports each under its display name, which may repeat.
     */
    data class Shared(val uris: List<String>) : FileSystemSource

    /**
     * App-private storage, scoped to one [bucket] directory under it.
     *
     * One bucket per hosted source, so two peers storing a same-named file here stay apart.
     */
    data class Internal(val bucket: String) : FileSystemSource {
        init {
            requireSingleSegment(bucket)
        }
    }
}

private fun requireSingleSegment(name: String) {
    require(name.isNotBlank()) { "Directory name cannot be blank" }
    require(name.none { it == '/' || it == '\\' } && name != "." && name != "..") {
        "Must be a single directory name: $name"
    }
}
