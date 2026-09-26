package com.fserver.core.disk

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.fserver.files.FilesNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * How full the device is and what this app keeps on it. Re-read periodically: nothing notifies
 * about free space changing.
 *
 * Directories are measured by walking them, so anything that lands in them - `:files` staging,
 * the image loader's disk cache, the storage backend's database - is counted without this class
 * knowing who wrote it. Hosted sources are skipped: the index already counts them.
 */
class DiskUsageRepository internal constructor(
    private val context: Context,
    private val filesNode: FilesNode,
) {
    val usage: Flow<DiskUsage> = flow {
        while (true) {
            emit(read())
            delay(RefreshInterval)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun read(): DiskUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val staging = filesNode.stagingRoot
        val caches = listOfNotNull(context.cacheDir, context.externalCacheDir)
        val skipped = caches + listOf(filesNode.internalRoot, File(context.dataDir, "code_cache"))

        return DiskUsage(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            footprint = AppFootprint(
                apkBytes = apkBytes(),
                stagingBytes = staging.treeSize(),
                cacheBytes = caches.sumOf { it.treeSize(skip = setOf(staging)) },
                serviceBytes = context.dataDir.treeSize(skip = skipped.toSet()),
            ),
        )
    }

    private fun apkBytes(): Long? = runCatching {
        val info = context.applicationInfo
        (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty())
            .sumOf { File(it).length() }
    }.getOrNull()?.takeIf { it > 0 }

    private companion object {
        val RefreshInterval = 30.seconds
    }
}

/**
 * Bytes under this directory, not descending into [skip]. Symlinks are not followed: the data
 * directory links out to the installed native libraries, which the APK size already covers.
 */
private fun File.treeSize(skip: Set<File> = emptySet()): Long {
    val skipped = skip.mapTo(mutableSetOf()) { it.absoluteFile }

    return walkTopDown()
        .onEnter { it.absoluteFile !in skipped && !it.isSymlink() }
        .filter { it.isFile && !it.isSymlink() }
        .sumOf { it.length() }
}

private fun File.isSymlink(): Boolean = runCatching {
    val parent = parentFile?.canonicalFile ?: return@runCatching false
    File(parent, name).canonicalPath != File(parent, name).absolutePath
}.getOrDefault(false)
