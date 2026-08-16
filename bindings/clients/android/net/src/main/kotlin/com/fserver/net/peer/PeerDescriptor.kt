package com.fserver.net.peer

import com.fserver.net.dictionary.MessageDictionary

/**
 * What a device turned out to be. Exchanged inside the sealed channel, so none of it is visible to
 * anyone who merely reached the address - which is the whole point of splitting it out of the
 * public hello.
 *
 * Everything here is confirmed by the handshake that carried it. The unconfirmed counterpart a
 * scan produces is [com.fserver.net.discovery.AdvertisedPeer], and the two are deliberately
 * different types so "claimed" can never be mistaken for "proven".
 *
 * The peer's public key is not here: it is proven by the handshake itself, and restating a proven
 * fact as a claim is how the two drift apart.
 */
data class PeerDescriptor(
    val deviceId: String,
    val displayName: String,
    val kind: String?,
    val dictionary: MessageDictionary.Descriptor,
    /**
     * The largest frame this peer will take. Negotiated here rather than in the public hello:
     * before the seal there is nothing to agree with, so each side just applies its own limit and
     * a stranger cannot move it.
     */
    val maxFrameSize: Int,
)
