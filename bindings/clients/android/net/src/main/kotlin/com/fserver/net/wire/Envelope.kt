package com.fserver.net.wire

import com.fserver.net.ProtocolException

/**
 * The header `:net` writes around every payload. Routing, correlation and queueing read this and
 * nothing else - which is exactly what keeps the dictionary opaque.
 */
internal class Envelope(
    val version: Int,
    val kind: FrameKind,
    val messageId: Long,
    /** Id of the message being answered; 0 when this frame answers nothing. */
    val correlationId: Long = NO_CORRELATION,
    val payload: ByteArray = EMPTY,
) {
    override fun equals(other: Any?): Boolean = other is Envelope &&
            version == other.version &&
            kind == other.kind &&
            messageId == other.messageId &&
            correlationId == other.correlationId &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + kind.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + correlationId.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String =
        "Envelope(v=$version, $kind, id=$messageId, corr=$correlationId, ${payload.size}b)"

    companion object {
        const val NO_CORRELATION: Long = 0L
        val EMPTY: ByteArray = ByteArray(0)
    }
}

/**
 * Frame types. [isControl] frames jump the send queue: a multi-hour transfer must not sit in
 * front of a keep-alive or a close.
 */
internal enum class FrameKind(val code: Int) {
    HELLO(1),
    HELLO_ACK(2),
    READY(3),

    MESSAGE(10),
    REQUEST(11),
    RESPONSE(12),
    ERROR(13),

    PING(20),
    PONG(21),
    CLOSE(22);

    val isControl: Boolean get() = code < 10 || code >= 20

    companion object {
        private val byCode = entries.associateBy(FrameKind::code)

        fun from(code: Int): FrameKind =
            byCode[code] ?: throw ProtocolException("unknown frame kind $code")
    }
}

internal object EnvelopeCodec {
    fun encode(envelope: Envelope): ByteArray = ByteWriter(HEADER_SIZE + envelope.payload.size)
        .u8(envelope.version)
        .u8(envelope.kind.code)
        .i64(envelope.messageId)
        .i64(envelope.correlationId)
        .bytes(envelope.payload)
        .toByteArray()

    fun decode(frame: ByteArray): Envelope {
        val reader = ByteReader(frame)
        return Envelope(
            version = reader.u8(),
            kind = FrameKind.from(reader.u8()),
            messageId = reader.i64(),
            correlationId = reader.i64(),
            payload = reader.bytes(),
        )
    }

    private const val HEADER_SIZE = 1 + 1 + 8 + 8 + 4
}
