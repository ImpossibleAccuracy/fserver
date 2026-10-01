package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.core.files.SyncFileEntry

class FileFetches internal constructor(
    private val receiving: Map<FileKey, Float?> = emptyMap(),
    private val asked: Set<FileKey> = emptySet(),
    private val failed: Set<FileKey> = emptySet(),
) {
    /** [entry]'s fetch, or null when none ran, or it is already here. */
    fun of(entry: SyncFileEntry): FileBrowserUi.File.Sync? {
        return when (val key = FileKey(entry.sourceId, entry.fileId)) {
            in receiving -> FileBrowserUi.File.Sync.Receiving(receiving[key])
            in asked -> FileBrowserUi.File.Sync.Receiving()
            in failed if entry.isRemote -> FileBrowserUi.File.Sync.Failed
            else -> null
        }
    }
}
