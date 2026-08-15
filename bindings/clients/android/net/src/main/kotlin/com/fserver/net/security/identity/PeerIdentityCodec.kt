package com.fserver.net.security.identity

import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/**
 * Wire form of an identity, for the [com.fserver.net.security.auth.AuthMethod] that has to carry one.
 *
 * It exists because identity left the public hello: a method now states who it is as part of
 * authenticating, which is also the only way the statement can be worth anything. Where the
 * exchange is not protected by the transport, a method is expected to seal these bytes under the
 * key it has just derived.
 */
object PeerIdentityCodec {
    fun encode(identity: LocalIdentity): ByteArray = ByteWriter(64)
        .string(identity.deviceId)
        .bytes(identity.publicKey)
        .toByteArray()

    fun decode(payload: ByteArray): PeerIdentity {
        val reader = ByteReader(payload)
        val deviceId = reader.string()
        val publicKey = reader.bytes()
        return PeerIdentity(deviceId = deviceId, publicKey = publicKey)
    }
}