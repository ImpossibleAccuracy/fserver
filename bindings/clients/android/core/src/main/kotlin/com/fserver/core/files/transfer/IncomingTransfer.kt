package com.fserver.core.files.transfer

interface IncomingTransfer {
    val filesCount: Int

    suspend fun accept()
    suspend fun reject()
}
