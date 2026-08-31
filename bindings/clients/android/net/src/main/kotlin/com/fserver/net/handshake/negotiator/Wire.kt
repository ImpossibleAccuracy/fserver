package com.fserver.net.handshake.negotiator

import com.fserver.common.exception.NetworkException
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlin.time.Duration

/**
 * One end of the conversation, before or after the seal. The same frame code drives both, so
 * moving a step across the seal is a matter of which wire it is handed.
 */
internal class Wire(
    val send: suspend (ByteArray) -> Result<Unit>,
    val next: suspend (Duration) -> ByteArray,
) {
    suspend fun write(kind: FrameKind, payload: ByteArray = Envelope.EMPTY) {
        send(
            Envelope.Codec.encode(
                Envelope(
                    version = ProtocolVersions.CURRENT,
                    kind = kind,
                    messageId = 0,
                    payload = payload,
                )
            )
        ).getOrThrow()
    }

    /** Wait for a frame of [kind], or throw if the peer closed or sent the wrong kind. */
    suspend fun expect(kind: FrameKind, timeout: Duration): Envelope {
        val envelope = Envelope.Codec.decode(next(timeout))
        if (envelope.kind == FrameKind.CLOSE) {
            throw NetworkException.Handshake("peer closed the handshake: ${envelope.payload.decodeToString()}")
        }
        if (envelope.kind != kind) {
            throw NetworkException.Handshake("expected $kind, got ${envelope.kind}")
        }
        return envelope
    }

    /** Tells the peer why before the caller fails - a silent drop looks like a broken network. */
    suspend fun sendClose(reason: String) {
        runCatching { write(FrameKind.CLOSE, reason.encodeToByteArray()) }
    }

    /** Runs [block], telling the peer why before the failure propagates. */
    suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: Throwable) {
        sendClose(e.message ?: e::class.simpleName.orEmpty())
        throw e
    }
}
