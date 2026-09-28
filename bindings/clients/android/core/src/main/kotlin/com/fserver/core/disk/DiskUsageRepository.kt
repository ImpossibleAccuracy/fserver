package com.fserver.core.disk

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.preview.EvictionPreviewer
import com.fserver.files.FilesNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * How full the device is and what this app keeps on it. Re-read periodically: nothing notifies
 * about free space changing.
 *
 * Directories are measured by walking them, so anything that lands in them - `:files` staging,
 * the image loader's disk cache, the storage backend's database - is counted without this class
 * knowing who wrote it. Hosted sources are skipped: the index already counts them. Eviction
 * previews are the exception: the host's [EvictionPreviewer] reports them, and they are taken out
 * of whichever category they sit in.
 */
class DiskUsageRepository internal constructor(
    private val context: Context,
    private val filesNode: FilesNode,
    private val previewer: EvictionPreviewer?,
) {
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val usage: Flow<DiskUsage> = flow {
        while (true) {
            emit(read())
            withTimeoutOrNull(RefreshInterval) { refresh.first() }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** Drops every eviction preview the host keeps - see [EvictionPreviewer.clear]. */
    suspend fun clearEvictionPreviews() {
        previewer?.clear()
        refresh.tryEmit(Unit)
    }

    private suspend fun read(): DiskUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val staging = filesNode.stagingRoot
        val caches = listOfNotNull(context.cacheDir, context.externalCacheDir)
        val skipped = caches + listOf(filesNode.internalRoot, File(context.dataDir, "code_cache"))
        val previews = previewUsage()

        return DiskUsage(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            footprint = AppFootprint(
                apkBytes = apkBytes(),
                stagingBytes = staging.treeSize(),
                cacheBytes = caches.sumOf { it.treeSize(skip = setOf(staging)) }.without(previews, StoreType.Cache),
                evictionPreviewBytes = previews.values.sum(),
                serviceBytes = context.dataDir.treeSize(skip = skipped.toSet()).without(previews, StoreType.AppData),
            ),
        )
    }

    // A broken previewer must not blank the whole screen: its bytes just stay where they sit.
    private suspend fun previewUsage(): Map<StoreType, Long> =
        runCatchingCancellable { previewer?.usage().orEmpty() }
            .onFailure { Timber.w(it, "Eviction previews could not be measured") }
            .getOrDefault(emptyMap())

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

// A walk racing a write can see less than the host reported; never below zero.
private fun Long.without(previews: Map<StoreType, Long>, type: StoreType): Long =
    (this - (previews[type] ?: 0)).coerceAtLeast(0)

private fun File.isSymlink(): Boolean = runCatching {
    val parent = parentFile?.canonicalFile ?: return@runCatching false
    File(parent, name).canonicalPath != File(parent, name).absolutePath
}.getOrDefault(false)
