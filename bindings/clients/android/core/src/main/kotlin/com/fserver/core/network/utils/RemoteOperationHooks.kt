package com.fserver.core.network.utils

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.IdGenerator
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.net.session.PeerSession
import kotlin.time.Duration


/**
 * Sends [operation] and suspends until the peer confirms it ran, rather than until the frame left.
 * A confirmation carrying another operation's id is an error, not a late answer to this one.
 */
internal suspend fun PeerSession<FileServerMessages>.runRemoteOperation(
    operation: RemoteOperation,
    timeout: Duration? = null,
) {
    val operationId = IdGenerator.nextId

    val request = FileServerMessages.OperationWithConfirmation(
        operationId = operationId,
        instance = operation,
    )

    when (val response = request(request, timeout).getOrThrow()) {
        is FileServerMessages.Response.OperationCompleted -> {
            if (response.operationId != operationId) {
                error("FileOperation response operationId does not match request: ${response.operationId} vs $operationId")
            }
        }

        // A refusal the peer explained. Kept distinct from a protocol error so the caller can log
        // why the peer said no instead of "unexpected response".
        is FileServerMessages.Response.OperationFailed -> {
            if (response.operationId != operationId) {
                error("FileOperation failure operationId does not match request: ${response.operationId} vs $operationId")
            }

            throw SyncException.RemoteRejectedException(
                "Peer ${identity.deviceId} refused $operation: ${response.reason}"
            )
        }

        else -> error("Unexpected response to FileOperation request: $response")
    }
}
