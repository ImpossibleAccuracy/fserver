package com.fserver.core.network.dictionary

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface FileServerMessages {
    sealed interface Request : FileServerMessages {
        @Serializable
        data class TransferRequest(
            val filesCount: Int,
        ) : Request

        @Serializable
        data object SavedFiles : Request
    }

    sealed interface Response : FileServerMessages {
        @Serializable
        data object ConfirmTransfer : Response

        @Serializable
        data object RejectTransfer : Response

        @Serializable
        data object SavedFiles : Response
    }
}
