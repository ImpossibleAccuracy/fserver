package com.fserver.net.wire

import com.fserver.net.ProtocolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvelopeCodecTest {

    @Test
    fun `survives a round trip`() {
        val original = Envelope(
            version = ProtocolVersions.CURRENT,
            kind = FrameKind.REQUEST,
            messageId = 42,
            correlationId = 7,
            payload = byteArrayOf(1, 2, 3, 0, -1),
        )

        assertEquals(original, EnvelopeCodec.decode(EnvelopeCodec.encode(original)))
    }

    @Test
    fun `carries an empty payload`() {
        val original = Envelope(ProtocolVersions.CURRENT, FrameKind.PING, messageId = 1)

        assertEquals(original, EnvelopeCodec.decode(EnvelopeCodec.encode(original)))
    }

    @Test
    fun `truncated frame is a protocol error, not a buffer error`() {
        val frame = EnvelopeCodec.encode(
            Envelope(ProtocolVersions.CURRENT, FrameKind.MESSAGE, 1, payload = byteArrayOf(9, 9, 9))
        )

        assertThrows(ProtocolException::class.java) {
            EnvelopeCodec.decode(frame.copyOf(frame.size - 2))
        }
    }

    @Test
    fun `unknown frame kind is rejected`() {
        val frame = EnvelopeCodec.encode(Envelope(ProtocolVersions.CURRENT, FrameKind.MESSAGE, 1))
        frame[1] = 99

        assertThrows(ProtocolException::class.java) { EnvelopeCodec.decode(frame) }
    }
}
