package com.fserver.app.presentation.screens.source.shared.preview.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.common.model.FileSize

/**
 * What a finished scan looks like on screen, before anything is done with it.
 *
 * A gallery source reads as tiles and a folder as rows, so the layout travels with the content
 * rather than being decided again by every screen that shows one.
 */
@Immutable
sealed interface SourcePreviewUi {
    val isEmpty: Boolean

    /** Flat rows, newest walk order. What a single folder reads as. */
    data class PlainList(
        val files: List<File>,
    ) : SourcePreviewUi {
        override val isEmpty: Boolean
            get() = files.isEmpty()
    }

    /** Tiles. What a gallery reads as. */
    data class Gallery(
        val files: List<File>,
    ) : SourcePreviewUi {
        override val isEmpty: Boolean
            get() = files.isEmpty()
    }

    /** Folders and the files in them, nested. What a whole device reads as. */
    data class Tree(
        val directories: List<PreviewContentEntry> = emptyList(),
    ) : SourcePreviewUi {
        override val isEmpty: Boolean
            get() = directories.isEmpty()

        /** The volume a single-volume tree is drawn already opened into, if it is one. */
        val root: Directory?
            get() = directories.singleOrNull()
                ?.let { it as? Directory }
                ?.takeIf { it.isVolume }

        /** What the top of the tree lists. */
        val rootContents: List<PreviewContentEntry>
            get() = root?.contents ?: directories

        /** The directory holding [entry], or null when [entry] already sits in [rootContents]. */
        fun parentOf(entry: PreviewContentEntry?): Directory? {
            val path = entry?.path ?: return null

            val parent = directories
                .firstNotNullOfOrNull {
                    (it as? Directory)?.findParent(path)
                }
                ?: return null

            return parent.takeIf { it.path != root?.path }
        }
    }


    /** A single file or directory in a [SourcePreviewUi]. */
    sealed interface PreviewContentEntry {
        val name: String
        val path: String
    }

    @Immutable
    data class File(
        val id: String,
        override val path: String,
        override val name: String,
        val kind: FileKindUi,
        /** Device-local address, for handing the file to a system viewer. */
        val locator: String?,
        val size: FileSize?,
        val location: Location? = null,
        /** Set on a non-media file, which has no thumbnail to fall back on in the gallery. */
        val extensionLabel: String?,
    ) : PreviewContentEntry {
        enum class Location {
            Local,
            Remote,
        }
    }

    @Immutable
    data class Directory(
        override val path: String,
        override val name: String,
        val files: Int,
        val size: FileSize,
        /** Files and folders directly in this directory. */
        val contents: List<PreviewContentEntry> = emptyList(),
        /** A storage volume rather than a folder on one. Only a whole-device scan has these. */
        val isVolume: Boolean = false,
    ) : PreviewContentEntry {
        val isMediaDirectory: Boolean
            get() = isMediaCollection(contents)

        fun filesCount(): Int = files + contents.sumOf {
            when (it) {
                is Directory -> it.filesCount()
                is File -> 1
            }
        }

        /** The directory directly holding [path], searched depth-first from here. */
        fun findParent(path: String): Directory? {
            for (child in contents) {
                if (child.path == path) return this
                if (child !is Directory) continue

                val found = child.findParent(path)
                if (found != null) return found
            }
            return null
        }

        fun findDirectory(path: String): Directory? {
            if (this.path == path) return this
            for (child in contents) {
                if (child !is Directory) continue

                val found = child.findDirectory(path)
                if (found != null) return found
            }
            return null
        }
    }

    companion object {
        const val MediaRatioThreshold = 0.7
    }
}
