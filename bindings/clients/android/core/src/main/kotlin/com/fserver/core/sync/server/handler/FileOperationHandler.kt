package com.fserver.core.sync.server.handler

import com.fserver.common.exception.SyncException
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.network.dictionary.dto.toFiles
import com.fserver.core.network.dictionary.dto.toIndexed
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.fileops.FileMover
import com.fserver.core.sync.index.LocalFileHasher
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.model.acceptsPeerWrites
import com.fserver.core.sync.model.peerDrivesSync
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.transfer.FileUploader
import com.fserver.net.session.PeerSession
import timber.log.Timber

/** Runs what the peer asks us to do to one file we hold: hash, delete, rename it, or send it back. */
internal class FileOperationHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val localHasher: LocalFileHasher,
    private val indexWriter: LocalIndexWriter,
    private val fileDeleter: FileDeleter,
    private val fileUploader: FileUploader,
    private val fileMover: FileMover,
) {
    suspend fun handle(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.File,
    ) {
        val source = authorizer.authorizedSource(session.identity, operation.key.sourceId)

        val file = storage.index.findFile(operation.key)
            ?: throw IllegalArgumentException("File ${operation.key.fileId} not found in source ${source.id}")

        val allowed = when (operation) {
            is RemoteOperation.File.Hash, is RemoteOperation.File.Download -> true
            is RemoteOperation.File.Delete, is RemoteOperation.File.Move -> source.acceptsPeerWrites
            is RemoteOperation.File.AdoptVersion -> source.peerDrivesSync
        }

        if (!allowed) {
            throw SyncException.ModeForbiddenException(
                "Source ${source.id} refuses ${operation::class.simpleName} from ${session.identity.deviceId} under ${source.syncMode.type}"
            )
        }

        when (operation) {
            is RemoteOperation.File.Hash -> localHasher.hashFile(source, file)

            is RemoteOperation.File.Delete -> {
                // Thrown, not logged: the peer records this as done on our side once we confirm.
                check(fileDeleter.delete(source, operation.key, operation.version?.toIndexed())) {
                    "Failed to delete ${file.path} from source ${source.id}"
                }

                Timber.i("Deleted file ${file.path} from source ${source.id} as requested by peer ${session.identity.deviceId}")
            }

            is RemoteOperation.File.Move -> {
                val target = operation.target.toFileRecord()

                fileMover.move(
                    source = source,
                    from = operation.key,
                    expected = ContentHash(value = operation.expected.value, algorithm = operation.expected.algorithm),
                    target = target,
                    version = target.metadata.version,
                    deletedVersion = operation.deletedVersion?.toFiles(),
                )
            }

            is RemoteOperation.File.AdoptVersion -> indexWriter.adoptVersion(
                source = source,
                key = operation.key,
                version = operation.version.toIndexed(),
                expected = operation.expected?.let { ContentHash(value = it.value, algorithm = it.algorithm) },
            )

            is RemoteOperation.File.Download -> {
                // Peer requests us to send them the file. It goes back over the session that asked
                // for it, which is not necessarily the one the source normally syncs over.
                val record = file.toFileRecord()

                fileUploader.uploadFile(
                    file = record,
                    version = operation.version?.toFiles() ?: record.metadata.version,
                    source = source,
                    session = session,
                )
            }
        }
    }
}
