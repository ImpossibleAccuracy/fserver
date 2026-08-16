package com.fserver.net.handshake.negotiator

import com.fserver.net.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.peer.PeerDescriptorCodec
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.FrameKind

/**
 * `DESCRIPTOR` / `READY`, run only after the channel is sealed. Nothing here is visible to anyone
 * who merely reached the address.
 */
internal class SealedPhase(
    private val configHolder: NetworkConfigHolder<*>,
) {
    private val config: NetworkConfig<*> get() = configHolder.current

    /** Both sides send and both read. After the seal there is no reason to take turns. */
    suspend fun exchangeDescriptors(
        identity: LocalIdentity,
        wire: Wire,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): PeerDescriptor {
        wire.write(FrameKind.DESCRIPTOR, PeerDescriptorCodec.encode(localDescriptor(identity, capabilities)))
        val incoming = wire.expect(FrameKind.DESCRIPTOR, policy.timeouts.handshake)
        return PeerDescriptorCodec.decode(incoming.payload)
    }

    /** The user's dictionary verdict, on a descriptor that arrived sealed. */
    suspend fun negotiateDictionary(wire: Wire, remote: MessageDictionary.Descriptor): Int =
        when (val decision = config.dictionary.negotiate(remote)) {
            is MessageDictionary.Decision.Accept -> decision.effectiveVersion
            is MessageDictionary.Decision.Reject -> {
                wire.sendClose("dictionary rejected: ${decision.reason}")
                throw NetworkException.DictionaryMismatch(remote, decision.reason)
            }
        }

    suspend fun confirmReady(wire: Wire, role: CryptoProvider.Role, policy: ConnectionPolicy) =
        when (role) {
            CryptoProvider.Role.Initiator -> wire.write(FrameKind.READY)
            CryptoProvider.Role.Responder -> {
                wire.expect(FrameKind.READY, policy.timeouts.handshake)
                Unit
            }
        }

    /** What this side sends over `DESCRIPTOR`. */
    private fun localDescriptor(
        identity: LocalIdentity,
        capabilities: TransportCapabilities,
    ): PeerDescriptor = PeerDescriptor(
        deviceId = identity.deviceId,
        displayName = identity.displayName,
        kind = identity.kind,
        dictionary = config.dictionary.descriptor,
        maxFrameSize = capabilities.maxFrameSize,
    )
}
