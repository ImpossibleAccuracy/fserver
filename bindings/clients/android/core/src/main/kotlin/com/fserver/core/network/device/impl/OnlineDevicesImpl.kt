package com.fserver.core.network.device.impl

import com.fserver.core.network.NetworkController
import com.fserver.core.network.device.OnlineDevices
import com.fserver.core.network.device.impl.mapper.toDomain
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.impl.asTransportKind
import com.fserver.core.store.FServerStorage
import com.fserver.net.connection.HandshakeProfile
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Merges the three things that make a device visible into one snapshot, and hands each of them out
 * separately.
 *
 * Merged rather than exposed raw because the same device shows up in several of them at once - a
 * peer that was discovered, then connected, is one device with two routes, not two entries - and
 * whichever claim is strongest is the one it is reported under.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OnlineDevicesImpl(
    private val network: NetworkController,
    private val storage: FServerStorage,
) : OnlineDevices {

    /**
     * Sessions with a link actually in place.
     *
     * A session outlives its link - it drops back to [PeerSession.State.Connecting] and rebuilds
     * itself, and a dead one sits in the registry until its terminal state lands - so the registry
     * on its own reports a device that walked away as connected until something else clears it.
     * The state of each is watched rather than read once, because nothing re-emits the registry
     * when one of them changes.
     */
    private val liveSessions: Flow<List<PeerSession<*>>> =
        network.incomingConnections.sessions.flatMapLatest { sessions ->
            if (sessions.isEmpty()) return@flatMapLatest flowOf(emptyList())

            combine(sessions.map { session -> session.state.map { session to it } }) { states ->
                states.filter { (_, state) -> state is PeerSession.State.Ready }
                    .map { (session, _) -> session }
            }
        }

    private val snapshot: Flow<Snapshot> = combine(
        network.peerDiscovery.peers,
        liveSessions,
        network.requestManager.profiles,
    ) { peers, sessions, profiles -> merge(peers, sessions, profiles) }

    override val all: Flow<List<ForeignDevice>> =
        snapshot.map { it.all }.distinctUntilChanged()

    override val connected: Flow<List<ForeignDevice>> =
        snapshot.map { it.connected }.distinctUntilChanged()

    override val handshaken: Flow<List<ForeignDevice>> =
        snapshot.map { it.handshaken }.distinctUntilChanged()

    override val discovered: Flow<List<ForeignDevice>> =
        snapshot.map { it.discovered }.distinctUntilChanged()

    override val known: Flow<List<ForeignDevice>> =
        combine(all, storage.trust.knownDeviceIds) { devices, known ->
            devices.filter { it.deviceId in known }
        }

    override val unknown: Flow<List<ForeignDevice>> =
        combine(all, storage.trust.knownDeviceIds) { devices, known ->
            devices.filterNot { it.deviceId in known }
        }

    override fun device(id: String): Flow<ForeignDevice?> = all.map { list ->
        list.find { it.deviceId == id }
    }

    private fun merge(
        peers: List<DiscoveredPeer>,
        sessions: List<PeerSession<*>>,
        profiles: Map<String, HandshakeProfile>,
    ): Snapshot {
        // Both maps are drained as the stronger claims are answered, so whatever is left over is
        // known by that way and no other.
        val peersByIds = peers.associateByTo(mutableMapOf()) { it.advertised.deviceId }
        val unusedProfiles = profiles.toMutableMap()

        val connected = sessions.map { session ->
            val peer = peersByIds.remove(session.identity.deviceId)
            val handshake = unusedProfiles.remove(session.identity.deviceId)

            ForeignDevice(
                deviceId = session.identity.deviceId,
                displayName = session.descriptor.displayName,
                kind = DeviceKind.fromSerialized(session.descriptor.kind),
                routes = listOf(session.route)
                    .plus(peer?.routes ?: emptyList())
                    .distinctBy { it.transport }
                    .map { it.toDomain() },
                foundBy = session.route.transport.asTransportKind(),
                lastSeen = Instant.now(),
                handshake = handshake?.let { it.identity.toDomain(it.negotiated) },
                hasSession = true,
            )
        }

        val handshaken = unusedProfiles.map { (_, profile) ->
            val peer = peersByIds.remove(profile.identity.deviceId)
            val foundBy = peer?.routes?.first()?.transport

            ForeignDevice(
                deviceId = profile.identity.deviceId,
                displayName = profile.negotiated.peerDescriptor.displayName,
                kind = DeviceKind.fromSerialized(profile.negotiated.peerDescriptor.kind),
                routes = listOf(profile.route.toDomain()),
                foundBy = foundBy.asTransportKind(),
                lastSeen = Instant.now(),
                handshake = profile.identity.toDomain(profile.negotiated),
                hasSession = false,
            )
        }

        val discovered = peersByIds.map { (_, peer) ->
            ForeignDevice(
                deviceId = peer.advertised.deviceId,
                displayName = peer.advertised.displayName,
                kind = DeviceKind.fromSerialized(peer.advertised.kind),
                routes = peer.routes.map { it.toDomain() },
                foundBy = peer.routes.first().transport.asTransportKind(),
                lastSeen = peer.lastSeen,
                handshake = null,
                hasSession = false,
            )
        }

        return Snapshot(
            connected = connected,
            handshaken = handshaken,
            discovered = discovered,
        )
    }

    /** One pass over the three sources, so every flow below reports the same moment. */
    private data class Snapshot(
        val connected: List<ForeignDevice>,
        val handshaken: List<ForeignDevice>,
        val discovered: List<ForeignDevice>,
    ) {
        val all: List<ForeignDevice> get() = connected + handshaken + discovered
    }
}
