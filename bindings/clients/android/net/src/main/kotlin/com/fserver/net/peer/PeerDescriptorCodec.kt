package com.fserver.net.peer

import com.fserver.net.security.Fingerprint
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/** Wire form of [PeerDescriptor] - the payload of `DESCRIPTOR_REQUEST`/`DESCRIPTOR_RESPONSE`. */
internal object PeerDescriptorCodec {
    fun encode(descriptor: PeerDescriptor): ByteArray {
        val writer = ByteWriter(128)
            .string(descriptor.deviceId)
            .string(descriptor.displayName)
            .i32(descriptor.kind?.ordinal ?: NONE)
            .i32(descriptor.accessMode?.ordinal ?: NONE)

        val advertised = descriptor.advertised
        writer.bool(advertised.protocolVersions != null)
        advertised.protocolVersions?.let { writer.i32(it.first).i32(it.last) }

        writer.bool(advertised.fingerprint != null)
        advertised.fingerprint?.let { writer.string(it.value) }

        writer.bool(advertised.dictionaryId != null)
        advertised.dictionaryId?.let { writer.string(it) }

        writer.i32(advertised.dictionaryVersion ?: NONE)

        return writer.toByteArray()
    }

    fun decode(payload: ByteArray): PeerDescriptor {
        val reader = ByteReader(payload)
        val deviceId = reader.string()
        val displayName = reader.string()
        val kind = PeerDescriptor.Kind.entries.getOrNull(reader.i32())
        val accessMode = PeerDescriptor.AccessMode.entries.getOrNull(reader.i32())

        val protocolVersions = if (reader.bool()) reader.i32()..reader.i32() else null
        val fingerprint = if (reader.bool()) Fingerprint(reader.string()) else null
        val dictionaryId = if (reader.bool()) reader.string() else null
        val dictionaryVersion = reader.i32().takeIf { it != NONE }

        return PeerDescriptor(
            deviceId = deviceId,
            displayName = displayName,
            kind = kind,
            accessMode = accessMode,
            advertised = PeerDescriptor.Advertised(
                protocolVersions = protocolVersions,
                fingerprint = fingerprint,
                dictionaryId = dictionaryId,
                dictionaryVersion = dictionaryVersion,
            ),
        )
    }

    private const val NONE = -1
}
