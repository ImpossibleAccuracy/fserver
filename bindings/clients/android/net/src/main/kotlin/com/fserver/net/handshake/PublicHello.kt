package com.fserver.net.handshake

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/**
 * The public half of the handshake, and the only part that goes out in the clear. Sent as `HELLO`
 * by the initiator and `HELLO_ACK` by the responder, which fills [minVersion] and [maxVersion]
 * with the version it chose.
 *
 * Three fields, and adding a fourth is a change to the threat model rather than a new feature.
 * Anything that says *which* device this is - the id, the name, the kind, the dictionary, the
 * long-term key - travels later: the id and key inside the chosen
 * [com.fserver.net.security.auth.AuthMethod], the rest inside the sealed channel as a
 * [com.fserver.net.peer.PeerDescriptor]. Even the maximum frame size is gone; before the seal each
 * side simply applies its own.
 *
 * [methods] is a claim, not an instruction. Each side checks any choice against what its *own*
 * transport allows before running it.
 */
internal class PublicHello(
    val minVersion: Int,
    val maxVersion: Int,
    val methods: List<AuthMethodId>,
) {
    fun encode(): ByteArray {
        val writer = ByteWriter(64)
            .i32(minVersion)
            .i32(maxVersion)
            .i32(methods.size)
        methods.forEach { writer.string(it.value) }
        return writer.toByteArray()
    }

    companion object {
        /** A peer that offers more than this is not negotiating, it is probing the parser. */
        private const val MAX_METHODS = 32

        fun decode(payload: ByteArray): PublicHello {
            val reader = ByteReader(payload)
            val minVersion = reader.i32()
            val maxVersion = reader.i32()

            val methodCount = reader.i32()
            if (methodCount !in 0..MAX_METHODS) {
                throw NetworkException.Protocol("hello declares $methodCount auth methods")
            }

            return PublicHello(
                minVersion = minVersion,
                maxVersion = maxVersion,
                methods = List(methodCount) { AuthMethodId(reader.string()) },
            )
        }
    }
}
