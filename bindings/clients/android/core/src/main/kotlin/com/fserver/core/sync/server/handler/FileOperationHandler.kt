package com.fserver.core.sync.server.handler

import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.runner.FileUploader
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Runs what the peer asks us to do to one file we hold: hash it, delete it, or send it back. */
internal class FileOperationHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val localIndexer: LocalChangesIndexer,
    private val fileUploader: FileUploader,
    private val timeProvider: TimeProvider,
) {
    suspend fun handle(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.File,
    ) {
        val source = authorizer.authorizedSource(session.identity, operation.key.sourceId)

        val file = storage.index.findFile(operation.key)
            ?: throw IllegalArgumentException("File ${operation.key.fileId} not found in source ${source.id}")

        when (operation) {
            is RemoteOperation.File.Hash -> localIndexer.hashFile(source, file)

            is RemoteOperation.File.Delete -> withContext(NonCancellable) {
                val fs = node.openSource(source.location.toFiles())

                // Thrown, not logged: the peer records this as done on our side once we confirm.
                if (!fs.deleteFile(file.locator)) {
                    throw IllegalStateException("Failed to delete ${file.path} from source ${source.id}")
                }

                storage.index.updateFileState(
                    key = operation.key,
                    state = LocalIndexedFile.State.Deleted(deletedAt = timeProvider.now()),
                )

                Timber.i("Deleted file ${file.path} from source ${source.id} as requested by peer ${session.identity.deviceId}")
            }

            is RemoteOperation.File.Download -> {
                // Peer requests us to send them the file. It goes back over the session that asked
                // for it, which is not necessarily the one the source normally syncs over.
                fileUploader.uploadFile(
                    file = file.toFileRecord(),
                    source = source,
                    session = session,
                )
            }
        }
    }
}
