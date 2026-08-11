package com.fserver.net.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The transport channel with the session's [CryptoProvider.Aead] wrapped around it. Everything
 * above writes plaintext frames and never learns whether they were encrypted.
 *
 * Built over a frame source rather than the raw channel: the handshake already consumed part of
 * the stream, and re-collecting a transport flow could drop what arrived in between.
 */
internal class SecureChannel(
    inboundFrames: Flow<ByteArray>,
    private val writer: suspend (ByteArray) -> Result<Unit>,
    private val closer: () -> Unit,
    private val aead: CryptoProvider.Aead,
) {
    val inbound: Flow<ByteArray> = inboundFrames.map(aead::open)

    suspend fun send(frame: ByteArray): Result<Unit> = writer(aead.seal(frame))

    fun close() = closer()
}
