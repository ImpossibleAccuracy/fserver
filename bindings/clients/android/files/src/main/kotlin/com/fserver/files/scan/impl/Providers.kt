package com.fserver.files.scan.impl

import android.content.ContentResolver
import android.net.Uri
import com.fserver.common.exception.FileSystemException
import com.fserver.files.scan.FoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A provider that loads file content from a given URI. */
internal class UriContentProvider(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : FoundFile.ContentProvider {
    override val locator: String
        get() = uri.toString()

    override suspend fun loadBytes(): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw FileSystemException.InvalidPath(uri.toString())
    }
}

/** A provider that loads file content from a given [File]. */
internal class FileContentProvider(private val file: File) : FoundFile.ContentProvider {
    override val locator: String
        get() = file.absolutePath

    override suspend fun loadBytes(): ByteArray = withContext(Dispatchers.IO) {
        file.readBytes()
    }
}
