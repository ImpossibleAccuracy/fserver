package com.fserver.net.wire

/**
 * The header `:net` writes around every payload. Routing, correlation and queueing read this and
 * nothing else - which is exactly what keeps the dictionary opaque.
 */
internal class Envelope(
    val version: Int,
    val kind: FrameKind,
    val messageId: Long,
    /** ID of the message being answered; 0 when this frame answers nothing. */
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

    object Codec {
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

    companion object {
        const val NO_CORRELATION: Long = 0L
        val EMPTY: ByteArray = ByteArray(0)
    }
}
