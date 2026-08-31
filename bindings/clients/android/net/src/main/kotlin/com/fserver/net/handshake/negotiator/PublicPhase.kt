package com.fserver.net.handshake.negotiator

import com.fserver.common.exception.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.handshake.PublicHello
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.FrameKind

/** What the public half settled. */
internal class PublicHalfResult(
    val remote: PublicHello,
    val protocolVersion: Int,
    val prologue: ByteArray,
) {
    fun greeting() = PublicGreeting(
        protocolVersions = remote.minVersion..remote.maxVersion,
        methods = remote.methods,
    )
}

/**
 * `HELLO` / `HELLO_ACK`: version negotiation and the auth-method claim, in the clear.
 */
internal class PublicPhase(
    private val configHolder: NetworkConfigHolder<*>,
    private val authPhase: AuthPhase,
) {
    private val config: NetworkConfig<*> get() = configHolder.current

    /** Initiate: send `HELLO`, get `HELLO_ACK`, and check the version is acceptable. */
    suspend fun initiate(
        wire: Wire,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): PublicHalfResult {
        val localHello = hello(capabilities, config.protocolVersions).encode()
        wire.write(FrameKind.HELLO, localHello)

        val ack = wire.expect(FrameKind.HELLO_ACK, policy.timeouts.handshake)
        val remote = PublicHello.decode(ack.payload)

        val version = remote.maxVersion
        if (version !in config.protocolVersions) {
            val reason = "protocol version $version is outside ${config.protocolVersions}"
            wire.sendClose(reason)
            throw NetworkException.Handshake(reason)
        }

        return PublicHalfResult(
            remote = remote,
            protocolVersion = version,
            prologue = localHello + ack.payload
        )
    }

    /** Respond: get `HELLO`, check the version is acceptable, and send `HELLO_ACK`. */
    suspend fun respond(
        wire: Wire,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): PublicHalfResult {
        val incoming = wire.expect(FrameKind.HELLO, policy.timeouts.handshake)
        val remote = PublicHello.decode(incoming.payload)

        val version = minOf(remote.maxVersion, config.protocolVersions.last)
        if (version < maxOf(remote.minVersion, config.protocolVersions.first)) {
            val reason =
                "no common protocol version: peer ${remote.minVersion}..${remote.maxVersion}, local ${config.protocolVersions}"
            wire.sendClose(reason)
            throw NetworkException.Handshake(reason)
        }

        val localHello = hello(capabilities, version..version).encode()
        wire.write(FrameKind.HELLO_ACK, localHello)

        return PublicHalfResult(remote, version, incoming.payload + localHello)
    }

    /** Build a hello frame for the peer to decode. */
    private fun hello(capabilities: TransportCapabilities, versions: IntRange): PublicHello =
        PublicHello(
            minVersion = versions.first,
            maxVersion = versions.last,
            methods = authPhase.offered(capabilities).map { it.id },
        )
}
