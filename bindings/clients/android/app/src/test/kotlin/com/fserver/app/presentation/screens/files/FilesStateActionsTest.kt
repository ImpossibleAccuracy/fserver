package com.fserver.app.presentation.screens.files

import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.screens.files.model.FilesState
import com.fserver.app.presentation.screens.files.model.FilesState.FileActionUi.Delete
import com.fserver.app.presentation.screens.files.model.FilesState.FileActionUi.Pin
import com.fserver.app.presentation.screens.files.model.FilesState.FileActionUi.Rename
import com.fserver.app.presentation.screens.files.model.FilesState.FileActionUi.Unpin
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileKey
import com.fserver.common.model.FileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesStateActionsTest {
    @Test
    fun mixedSelectionOffersPin() {
        val state = state(selected = setOf(Pinned, Unpinned))

        assertEquals(setOf(Delete, Pin), state.selectionActions)
    }

    @Test
    fun allPinnedSelectionOffersUnpin() {
        val state = state(selected = setOf(Pinned))

        assertEquals(setOf(Rename, Delete, Unpin), state.selectionActions)
    }

    @Test
    fun selectionWithUnpinnableFileOffersNoPin() {
        val state = state(selected = setOf(Pinned, Evicted))

        assertEquals(setOf(Delete), state.selectionActions)
    }

    @Test
    fun sameFileIdInAnotherSourceIsAnotherFile() {
        val twin = FileKey(fileId = Pinned.fileId, sourceId = "documents")
        val state = state(actions = Actions + (twin to setOf(Delete)), selected = setOf(twin))

        assertEquals(setOf(Delete), state.selectionActions)
        assertEquals("documents", state.file(twin)?.key?.sourceId)
    }

    @Test
    fun selectedFolderOffersPinRenameDelete() {
        val state = state(folders = setOf("/DCIM"))

        assertEquals(setOf(Rename, Delete, Pin), state.selectionActions)
        assertEquals(setOf(Pinned, Unpinned), state.selectionPinTargets)
    }

    @Test
    fun fullyPinnedFolderOffersUnpin() {
        val state = state(folders = setOf("/DCIM"), actions = Actions + (Unpinned to setOf(Rename, Delete, Unpin)))

        assertEquals(setOf(Rename, Delete, Unpin), state.selectionActions)
    }

    @Test
    fun folderAndFileTogetherDropRename() {
        val state = state(selected = setOf(Pinned), folders = setOf("/DCIM"))

        assertEquals(setOf(Delete, Pin), state.selectionActions)
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun folderWithoutPinnableFilesOffersNoPin() {
        val state = state(folders = setOf("/DCIM"), actions = Actions.mapValues { setOf(Delete) })

        assertEquals(setOf(Rename, Delete), state.selectionActions)
    }

    @Test
    fun folderWithReadOnlyFileOffersNoRenameDelete() {
        val state = state(folders = setOf("/DCIM"), actions = Actions - Evicted)

        assertEquals(setOf(Pin), state.selectionActions)
    }

    private fun state(
        actions: Map<FileKey, Set<FilesState.FileActionUi>> = Actions,
        selected: Set<FileKey> = emptySet(),
        folders: Set<String> = emptySet(),
    ) = FilesState(
        entries = FilesState.FeedUi(
            preview = FileBrowserUi.Tree(
                directories = listOf(
                    FileBrowserUi.Directory(
                        path = "/DCIM",
                        name = "DCIM",
                        files = 0,
                        size = FileSize(0),
                        contents = Actions.keys.map(::file),
                    ),
                ) + (actions.keys - Actions.keys).map(::file),
            ),
            filter = FilesState.FilterUi.All,
            sourceId = null,
            actions = actions,
        ),
        editing = selected.isNotEmpty() || folders.isNotEmpty(),
        selected = selected,
        selectedFolders = folders,
    )

    private fun file(key: FileKey) = FileBrowserUi.File(
        key = key,
        path = "/${key.sourceId}/${key.fileId}.jpg",
        name = "${key.fileId}.jpg",
        kind = FileKindUi.Image,
        locator = null,
        size = null,
        extensionLabel = null,
    )

    private companion object {
        val Pinned = FileKey(fileId = "pinned", sourceId = "camera")
        val Unpinned = FileKey(fileId = "unpinned", sourceId = "camera")
        val Evicted = FileKey(fileId = "evicted", sourceId = "camera")

        val Actions = mapOf(
            Pinned to setOf(Rename, Delete, Unpin),
            Unpinned to setOf(Rename, Delete, Pin),
            Evicted to setOf(Delete),
        )
    }
}
