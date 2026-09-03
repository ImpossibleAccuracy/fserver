package com.fserver.net.peer

import com.fserver.common.exception.NetworkException
import com.fserver.net.connection.SessionConfig
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportCapabilities.Companion.MIN_FRAME_SIZE
import com.fserver.net.wire.ByteWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameSizeBoundsTest {

    @Test
    fun `a transport below the minimum frame size is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransportCapabilities(maxFrameSize = MIN_FRAME_SIZE - 1)
        }
    }

    @Test
    fun `an assembly ceiling under one frame is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionConfig(maxAssembledMessageSize = MIN_FRAME_SIZE - 1)
        }
    }

    @Test
    fun `a peer declaring a frame limit under the minimum is refused`() {
        val refusal = assertThrows(NetworkException.Protocol::class.java) {
            PeerDescriptorCodec.decode(descriptorBytes(maxFrameSize = 64))
        }

        assertEquals(true, refusal.message?.contains("64"))
    }

    @Test
    fun `a peer at the minimum is accepted`() {
        val decoded = PeerDescriptorCodec.decode(descriptorBytes(MIN_FRAME_SIZE))

        assertEquals(MIN_FRAME_SIZE, decoded.maxFrameSize)
    }

    /** Hand-built so the frame limit can be anything a peer might put on the wire. */
    private fun descriptorBytes(maxFrameSize: Int): ByteArray = ByteWriter(128)
        .string("peer")
        .string("")
        .string("test.dictionary")
        .i32(1)
        .i32(1)
        .i32(1)
        .i32(maxFrameSize)
        .toByteArray()
}
