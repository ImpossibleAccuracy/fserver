package com.fserver.app.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** How full the phone's own storage is. Platform glue, so it lives here rather than in `:core`. */
class PhoneStorage(
    private val context: Context,
) {
    /** Re-read periodically: nothing notifies about free space changing. */
    val usage: Flow<Usage> = flow {
        while (true) {
            emit(read())
            delay(RefreshInterval)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun read(): Usage {
        val stat = StatFs(Environment.getDataDirectory().path)

        return Usage(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            apkBytes = apkBytes(),
        )
    }

    /** The installed APK and its splits, or null when the platform will not say. */
    private fun apkBytes(): Long? = runCatching {
        val info = context.applicationInfo
        (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty())
            .sumOf { File(it).length() }
    }.getOrNull()?.takeIf { it > 0 }

    data class Usage(
        val totalBytes: Long,
        val freeBytes: Long,
        val apkBytes: Long?,
    )

    private companion object {
        val RefreshInterval = 30.seconds
    }
}
