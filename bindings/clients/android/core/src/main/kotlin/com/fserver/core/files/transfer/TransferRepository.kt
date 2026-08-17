package com.fserver.core.files.transfer

import kotlinx.coroutines.flow.StateFlow

interface TransferRepository {
    val incomingTransfer: StateFlow<IncomingTransfer?>

    fun listenForTransfers()

    suspend fun sendFiles(deviceId: String, filesCount: Int): Result<Boolean>
}
