package com.fserver.files.scan.impl

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.fserver.files.model.ScanSource
import com.fserver.files.scan.DirectoryScanProgress
import com.fserver.files.scan.DirectoryScanner
import com.fserver.files.scan.ScannedFile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.collections.plusAssign

internal class DirectoryScannerImpl(
    private val context: Context,
) : DirectoryScanner {
    override suspend fun scan(
        directory: ScanSource,
        onProgress: (DirectoryScanProgress) -> Unit,
    ): List<ScannedFile> =
        when (directory) {
            is ScanSource.Root ->
                scanDirectories(directory.rootPaths, onProgress)

            is ScanSource.Tree -> scanTree(directory.path, onProgress)

            is ScanSource.Media -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStore(onProgress = onProgress)
            } else {
                TODO("Add files scan for pre-Android 10")
            }
        }

    /** Process multiple directory trees recursively. */
    private suspend fun scanDirectories(
        directories: List<String>,
        onProgress: (DirectoryScanProgress) -> Unit,
    ): List<ScannedFile> = coroutineScope {
        val foundFiles = mutableListOf<ScannedFile>()
        var totalSize = 0L

        val jobs = directories.map { directory ->
            launch {
                DirectoryFilesScanner.scanDirectory(
                    directoryPath = directory,
                    onFileFound = { file ->
                        foundFiles += file
                        totalSize += file.size

                        onProgress(
                            DirectoryScanProgress(
                                scannedFiles = foundFiles.size,
                                scannedSizeBytes = totalSize,
                            )
                        )
                    }
                )
            }
        }

        jobs.joinAll()

        return@coroutineScope foundFiles
    }

    /** Process a directory tree recursively. */
    private suspend fun scanTree(
        dirPath: String,
        onProgress: (DirectoryScanProgress) -> Unit,
    ): List<ScannedFile> {
        val foundFiles = mutableListOf<ScannedFile>()
        var totalSize = 0L

        RecursiveTreeScanner.scanTree(
            context = context,
            dirPath = dirPath,
            onFileFound = { file ->
                foundFiles += file
                totalSize += file.size

                onProgress(
                    DirectoryScanProgress(
                        scannedFiles = foundFiles.size,
                        scannedSizeBytes = totalSize,
                    )
                )
            }
        )

        return foundFiles
    }

    /** Query the MediaStore for all media files. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun queryMediaStore(
        onProgress: (DirectoryScanProgress) -> Unit,
    ): List<ScannedFile> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val foundMedia = mutableListOf<ScannedFile>()
        var totalSize = 0L

        context.contentResolver
            .query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val bucketIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                )

                while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                    val uri = ContentUris.withAppendedId(
                        collection,
                        cursor.getLong(idIndex),
                    )

                    totalSize += if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                    foundMedia plusAssign ScannedFile(
                        path = uri.toString(),
                        directory = cursor.getString(bucketIndex) ?: "Unknown",
                        size =
                            if (cursor.isNull(sizeIndex)) 0L
                            else cursor.getLong(sizeIndex),
                    )

                    onProgress(
                        DirectoryScanProgress(
                            scannedFiles = foundMedia.size,
                            scannedSizeBytes = totalSize,
                        )
                    )
                }
            }

        return foundMedia
    }
}