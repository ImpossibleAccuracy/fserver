package com.fserver.net.peer

import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/**
 * Wire form of [PeerDescriptor] - the payload of `DESCRIPTOR`.
 */
internal object PeerDescriptorCodec {
    fun encode(descriptor: PeerDescriptor): ByteArray = ByteWriter(128)
        .string(descriptor.displayName)
        .string(descriptor.kind ?: "")
        .string(descriptor.dictionary.id)
        .i32(descriptor.dictionary.version)
        .i32(descriptor.dictionary.supported.first)
        .i32(descriptor.dictionary.supported.last)
        .i32(descriptor.maxFrameSize)
        .toByteArray()

    fun decode(payload: ByteArray): PeerDescriptor {
        val reader = ByteReader(payload)
        // Read into locals: field order is the wire format, not an argument-evaluation detail.
        val displayName = reader.string()
        val kind = reader.string().ifBlank { null }
        val dictionaryId = reader.string()
        val dictionaryVersion = reader.i32()
        val supportedFrom = reader.i32()
        val supportedTo = reader.i32()
        val maxFrameSize = reader.i32()

        return PeerDescriptor(
            displayName = displayName,
            kind = kind,
            dictionary = MessageDictionary.Descriptor(
                id = dictionaryId,
                version = dictionaryVersion,
                supported = supportedFrom..supportedTo,
            ),
            maxFrameSize = maxFrameSize,
        )
    }
}
