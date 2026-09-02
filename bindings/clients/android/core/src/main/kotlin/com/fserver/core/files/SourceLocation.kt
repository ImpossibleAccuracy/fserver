package com.fserver.core.files

import com.fserver.common.utils.SourcePaths

sealed interface SourceLocation {
    /** The locations a user may point a source at. */
    sealed interface Selectable : SourceLocation

    /** Where files a peer sends may be written */
    sealed interface Hostable : SourceLocation

    /**
     * Whole storage volumes. Scannable only - a source is one directory, so a user pointing at
     * everything picks a [Directory] out of what a scan over this found.
     */
    data class Root(val volumes: List<Volume>) : SourceLocation {
        init {
            require(volumes.isNotEmpty()) { "Volumes list cannot be empty" }
        }

        override fun toString(): String {
            return "Root(volumes=${volumes.joinToString { it.id }})"
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
    }

    /** One directory, addressed by the Storage Access Framework tree the user granted. */
    data class Tree(val path: String) : Selectable, Hostable {
        override fun toString(): String {
            return "Tree(path=$path)"
        }
    }

    data object Media : Selectable {
        override fun toString(): String {
            return "Media"
        }
    }

    /**
     * One directory on a storage volume, addressed by path - [Root] scoped down to a single
     * folder, and only reachable with a grant wide enough to write outside the app's own storage.
     */
    data class Directory(val path: String) : Selectable, Hostable {
        override fun toString(): String {
            return "Directory(path=$path)"
        }
    }

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

        override fun toString(): String {
            return "Internal(bucket=$bucket)"
        }
    }
}
