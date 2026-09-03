package com.fserver.net.security

import com.fserver.net.security.crypto.CryptoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * The transport channel with the session's [com.fserver.net.security.crypto.CryptoProvider.Aead] wrapped around it.
 * Everything above writes plaintext frames and never learns whether they were encrypted.
 *
 * Built over frame accessors rather than the raw channel: the handshake already consumed part of
 * the stream, and re-collecting a transport flow could drop what arrived in between.
 *
 * [next] exists because the handshake is not finished when this is built - the descriptors are
 * still to come, and they go through here so they are never in the clear.
 * [inbound] is deliberately lazy: taking the rest of the stream before
 * the handshake has stopped pulling single frames off it would have the two race for the same frames.
 */
internal class SecureChannel(
    private val reader: suspend (Duration) -> ByteArray,
    remainingFrames: () -> Flow<ByteArray>,
    private val writer: suspend (ByteArray) -> Result<Unit>,
    private val closer: () -> Unit,
    private val aead: CryptoProvider.Aead,
) {
    val inbound: Flow<ByteArray> by lazy { remainingFrames().map(aead::open) }

    /** What sealing costs a frame - subtract it from the transport's limit, never from a guess. */
    val overhead: Int get() = aead.overhead

    /** One frame, for the part of the handshake that runs sealed. */
    suspend fun next(timeout: Duration): ByteArray = aead.open(reader(timeout))

    suspend fun send(frame: ByteArray): Result<Unit> = writer(aead.seal(frame))

    fun close() = closer()
}
