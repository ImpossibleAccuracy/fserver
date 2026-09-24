package com.fserver.files.fs.impl.media

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.StorageVolumeBuilder
import java.io.File
import java.io.FileNotFoundException

/**
 * An in-memory MediaStore for [MediaFileSystemTest], with bytes in [root].
 *
 * Models only what the backend leans on: `RELATIVE_PATH` under a directory MediaStore owns, the
 * rename of a colliding name, and a row another app owns refusing to be touched. The two
 * selections it understands are the two the backend sends.
 */
class FakeMediaProvider : ContentProvider() {

    private data class Row(
        val id: Long,
        val volume: String,
        val relativePath: String,
        val name: String,
        val mimeType: String,
        val file: File,
        val foreign: Boolean,
    )

    private val rows = mutableListOf<Row>()
    private var nextId = 1L

    private val root: File
        get() = File(requireNotNull(context).cacheDir, "fake-media")

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val matched = when {
            idOf(uri) != null -> rows.filter { it.id == idOf(uri) }
            selection == null -> rows.filter { inCollection(uri, it) }

            selection == "${FileColumns.RELATIVE_PATH} = ? AND ${FileColumns.DISPLAY_NAME} = ?" ->
                rows.filter {
                    inCollection(uri, it) &&
                        it.relativePath == selectionArgs!![0] &&
                        it.name == selectionArgs[1]
                }

            selection == "${FileColumns.MEDIA_TYPE} IN (?, ?, ?)" ->
                rows.filter { inCollection(uri, it) && mediaTypeOf(it).toString() in selectionArgs!! }

            else -> error("Selection not modelled: $selection")
        }

        val cursor = MatrixCursor(projection ?: arrayOf(FileColumns._ID))
        for (row in matched) {
            cursor.addRow(cursor.columnNames.map { valueOf(it, row) })
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val volume = volumeOf(uri)
        val relativePath = values!!.getAsString(FileColumns.RELATIVE_PATH)
        val mimeType = values.getAsString(FileColumns.MIME_TYPE)

        require(relativePath.substringBefore('/') in OwnedDirectories) {
            "Primary directory ${relativePath.substringBefore('/')} not allowed"
        }

        val name = freeName(volume, relativePath, values.getAsString(FileColumns.DISPLAY_NAME))
        val file = File(root, "$volume/$relativePath$name").apply {
            parentFile?.mkdirs()
            createNewFile()
        }

        val row = Row(nextId++, volume, relativePath, name, mimeType, file, foreign = false)
        rows += row

        return ContentUris.withAppendedId(uri, row.id)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val index = rows.indexOfFirst { it.id == idOf(uri) }
        if (index < 0) return 0

        val row = rows[index]
        if (row.foreign) throw SecurityException("${row.name} is owned by another app")

        val name = values!!.getAsString(FileColumns.DISPLAY_NAME) ?: return 0
        val renamed = freeName(row.volume, row.relativePath, name)
        val file = File(row.file.parentFile, renamed)

        row.file.renameTo(file)
        rows[index] = row.copy(name = renamed, file = file)

        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rows.firstOrNull { it.id == idOf(uri) } ?: return 0
        if (row.foreign) throw SecurityException("${row.name} is owned by another app")

        row.file.delete()
        rows -= row

        return 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rows.firstOrNull { it.id == idOf(uri) } ?: throw FileNotFoundException(uri.toString())

        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = rows.firstOrNull { it.id == idOf(uri) }?.mimeType

    /** A row another app put there: listed, but not this app's to rename or delete. */
    fun addForeign(relativePath: String, name: String, mimeType: String): Uri {
        val uri = insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(FileColumns.RELATIVE_PATH, relativePath)
                put(FileColumns.DISPLAY_NAME, name)
                put(FileColumns.MIME_TYPE, mimeType)
            },
        )

        val index = rows.indexOfFirst { it.id == ContentUris.parseId(uri) }
        rows[index] = rows[index].copy(foreign = true)

        return uri
    }

    /** MediaStore does not refuse a taken name: it picks the next free one. */
    private fun freeName(volume: String, relativePath: String, name: String): String {
        val taken = rows.filter { it.volume == volume && it.relativePath == relativePath }
            .map { it.name }
            .toSet()

        if (name !in taken) return name

        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }

        return generateSequence(1) { it + 1 }.map { "$base ($it)$extension" }.first { it !in taken }
    }

    private fun valueOf(column: String, row: Row): Any? = when (column) {
        FileColumns._ID -> row.id
        FileColumns.SIZE -> row.file.length()
        FileColumns.VOLUME_NAME -> row.volume
        FileColumns.RELATIVE_PATH -> row.relativePath
        FileColumns.DISPLAY_NAME -> row.name
        FileColumns.MIME_TYPE -> row.mimeType
        FileColumns.MEDIA_TYPE -> mediaTypeOf(row)
        // Seconds, as MediaStore keeps it.
        FileColumns.DATE_MODIFIED -> row.file.lastModified() / 1000
        else -> null
    }

    private fun mediaTypeOf(row: Row): Int = when (row.mimeType.substringBefore('/')) {
        "image" -> FileColumns.MEDIA_TYPE_IMAGE
        "video" -> FileColumns.MEDIA_TYPE_VIDEO
        "audio" -> FileColumns.MEDIA_TYPE_AUDIO
        else -> FileColumns.MEDIA_TYPE_NONE
    }

    /** `content://media/<volume>/file[/<id>]`. */
    private fun volumeOf(uri: Uri): String = uri.pathSegments[0]

    private fun idOf(uri: Uri): Long? = uri.pathSegments.getOrNull(2)?.toLongOrNull()

    private fun inCollection(uri: Uri, row: Row): Boolean =
        volumeOf(uri) == MediaStore.VOLUME_EXTERNAL || volumeOf(uri) == row.volume

    companion object {
        private val OwnedDirectories = setOf(
            "Alarms", "Audiobooks", "DCIM", "Documents", "Download", "Movies", "Music",
            "Notifications", "Pictures", "Podcasts", "Recordings", "Ringtones",
        )

        /**
         * Registers the provider and mounts the primary volume, which is what
         * `MediaStore.getExternalVolumeNames` reads.
         */
        fun register(): FakeMediaProvider {
            val context = RuntimeEnvironment.getApplication()

            shadowOf(context.getSystemService(StorageManager::class.java)).addStorageVolume(
                StorageVolumeBuilder(
                    "primary",
                    File(context.cacheDir, "fake-volume"),
                    "Primary",
                    android.os.Process.myUserHandle(),
                    android.os.Environment.MEDIA_MOUNTED,
                ).setIsPrimary(true).build(),
            )

            return Robolectric.buildContentProvider(FakeMediaProvider::class.java)
                .create(ProviderInfo().apply { authority = MediaStore.AUTHORITY })
                .get()
        }
    }
}
