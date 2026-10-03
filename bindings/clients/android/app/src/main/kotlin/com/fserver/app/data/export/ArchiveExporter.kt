package com.fserver.app.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fserver.common.task.ProgressTask
import com.fserver.common.task.progressTask
import com.fserver.core.external.export.DataExport
import com.fserver.core.external.export.ExportProgress
import com.fserver.core.external.export.ExportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** [DataExport] into a document the user picked. A failed or canceled export deletes the document. */
class ArchiveExporter(
    private val context: Context,
    private val export: DataExport,
) {
    /** Every source, or only [sourceIds]. */
    fun export(uri: Uri, sourceIds: Set<String>? = null): ProgressTask<ExportProgress, ExportReport> = progressTask {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw FileNotFoundException("Cannot write $uri")

                stream.buffered().use { out ->
                    val task = export.export(out, sourceIds)
                    task.progress.collect { send(it) }
                    task.result().getOrThrow()
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                throw e
            }
        }
    }
}
