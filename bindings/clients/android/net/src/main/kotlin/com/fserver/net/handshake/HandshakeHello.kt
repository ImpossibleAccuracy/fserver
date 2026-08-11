package com.fserver.net.handshake

import com.fserver.net.dictionary.DictionaryDescriptor
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/**
 * What each side says about itself before anything else. Sent as `HELLO` by the initiator and
 * `HELLO_ACK` by the responder, which fills [minVersion] and [maxVersion] with the version it chose.
 *
 * The dictionary appears here only as a descriptor - `:net` copies it across and hands it to the
 * user's own [com.fserver.net.dictionary.MessageDictionary.negotiate].
 */
internal class HandshakeHello(
    val minVersion: Int,
    val maxVersion: Int,
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val keyExchangeKey: ByteArray,
    val dictionary: DictionaryDescriptor,
    val maxFrameSize: Int,
    val cipherSuite: String,
) {
    fun encode(): ByteArray = ByteWriter(128)
        .i32(minVersion)
        .i32(maxVersion)
        .string(deviceId)
        .string(displayName)
        .bytes(publicKey)
        .bytes(keyExchangeKey)
        .string(dictionary.id)
        .i32(dictionary.version)
        .i32(dictionary.supported.first)
        .i32(dictionary.supported.last)
        .i32(maxFrameSize)
        .string(cipherSuite)
        .toByteArray()

    companion object {
        fun decode(payload: ByteArray): HandshakeHello {
            val reader = ByteReader(payload)
            return HandshakeHello(
                minVersion = reader.i32(),
                maxVersion = reader.i32(),
                deviceId = reader.string(),
                displayName = reader.string(),
                publicKey = reader.bytes(),
                keyExchangeKey = reader.bytes(),
                dictionary = DictionaryDescriptor(
                    id = reader.string(),
                    version = reader.i32(),
                    supported = reader.i32()..reader.i32(),
                ),
                maxFrameSize = reader.i32(),
                cipherSuite = reader.string(),
            )
        }
    }
}
