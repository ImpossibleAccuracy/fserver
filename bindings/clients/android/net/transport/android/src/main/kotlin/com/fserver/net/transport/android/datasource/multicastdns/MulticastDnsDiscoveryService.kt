package com.fserver.net.transport.android.datasource.multicastdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

private val VALID_PORT_RANGE = 1..65535

private typealias AwaitCloseTask = () -> Unit

internal class MulticastDnsDiscoveryService(
    private val context: Context,
) {
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val executor by lazy { Dispatchers.IO.asExecutor() }

    /**
     * Starts the mDNS discovery.
     *
     * @return flow of [Event] representing the mDNS events.
     */
    fun start(): Flow<Event> = callbackFlow {
        send(Event.Idle)

        val registry = ServiceRegistry()

        val discoveryListener = DiscoveryListener(
            nsdManager = nsdManager,
            scanStarted = {
                Timber.d("Discovery started for %s", SERVICE_TYPE)
                trySend(Event.Scanning)
            },
            scanStopped = {
                Timber.d("Discovery stopped for %s", SERVICE_TYPE)
                trySend(Event.Closed)
                close()
            },
            scanError = { error ->
                Timber.w("Discovery failed for %s, error=%d", SERVICE_TYPE, error)
                trySend(Event.Error(error))
                close()
            },
            serviceFound = { serviceInfo ->
                val key = serviceInfo.matchedServiceKey()

                // Null key = wrong service type. Failed claim = already being resolved.
                if (key != null && registry.claim(key)) {
                    Timber.d("Resolving %s", key)

                    resolveMdnsService(
                        key = key,
                        serviceInfo = serviceInfo,
                        registry = registry,
                        onError = { errorCode ->
                            Timber.w("Resolve failed for %s, error=%d", key, errorCode)
                            trySend(Event.PeerError(key, errorCode))
                        },
                        onResolved = { service ->
                            Timber.d("Resolved %s at %s:%d", key, service.host, service.port)
                            trySend(Event.Found(service))
                        },
                        onForgot = { peer ->
                            Timber.d("Peer %s disconnected", key)
                            trySend(Event.Disconnected(peer))
                        },
                    )
                }
            },
            serviceLost = { serviceInfo ->
                // Drop the de-dup entry so a peer that leaves and comes back is reported again.
                serviceInfo.matchedServiceKey()?.let { key ->
                    Timber.d("Service lost: %s", key)
                    registry.forget(key)?.let {
                        trySend(Event.Disconnected(it))
                    }
                }
            },
        )

        Timber.d("Starting mDNS discovery for %s", SERVICE_TYPE)

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )

        awaitClose {
            Timber.d("Tearing down mDNS discovery for %s", SERVICE_TYPE)

            // NSD throws when discovery already stopped on its own - teardown must not fail on it.
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
                .onFailure { Timber.w(it, "Could not stop discovery for %s", SERVICE_TYPE) }

            registry.clear()
        }
    }

    internal sealed interface Event {
        data object Idle : Event
        data object Scanning : Event
        data class Error(val errorCode: Int) : Event
        data object Closed : Event

        data class Found(val peer: MulticastDnsPeer) : Event
        data class Disconnected(val peer: MulticastDnsPeer) : Event
        data class PeerError(val id: String, val errorCode: Int) : Event
    }

    private fun resolveMdnsService(
        key: String,
        serviceInfo: NsdServiceInfo,
        registry: ServiceRegistry,
        onError: (Int) -> Unit,
        onResolved: (MulticastDnsPeer) -> Unit,
        onForgot: (MulticastDnsPeer) -> Unit,
    ) {
        /** Emits only when resolution produced a usable endpoint we have not published yet. */
        fun publish(resolved: NsdServiceInfo) {
            val service = resolved.toPeer(key)

            if (service == null) {
                Timber.v("Discarding %s: no usable endpoint", key)
                return
            }

            if (registry.accept(key, service)) {
                onResolved(service)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val listener = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(error: Int) {
                    // Never registered, so nothing to unregister - just release the claim.
                    Timber.w("ServiceInfoCallback registration failed for %s, error=%d", key, error)

                    registry.forget(key)
                    onError(error)
                }

                override fun onServiceInfoCallbackUnregistered() {
                    // No-op: unregistration is driven by forget()/clear(), which already cleaned up.
                    Timber.v("ServiceInfoCallback unregistered for %s", key)
                }

                override fun onServiceLost() {
                    Timber.d("Service lost while tracking %s", key)

                    registry.forget(key)?.let(onForgot)
                }

                override fun onServiceUpdated(service: NsdServiceInfo) {
                    // Fires on every TXT/address change; publish() filters out unchanged endpoints.
                    publish(service)
                }
            }

            nsdManager.registerServiceInfoCallback(serviceInfo, executor, listener)

            registry.onForget(key) {
                nsdManager.unregisterServiceInfoCallback(listener)
            }
        } else {
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // Release the claim so the next announcement retries this peer.
                    Timber.w("Legacy resolve failed for %s, error=%d", key, errorCode)

                    registry.forget(key)
                    onError(errorCode)
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                    publish(resolvedServiceInfo)
                }
            }

            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, listener)
        }
    }

    /**
     * Per-session memory for [MulticastDnsDiscoveryService].
     * Stores what discovery has already reported, for one collection of [start].
     */
    private class ServiceRegistry {
        private val claimed = ConcurrentHashMap.newKeySet<String>()
        private val published = ConcurrentHashMap<String, MulticastDnsPeer>()
        private val cleanups = ConcurrentHashMap<String, AwaitCloseTask>()

        /** `true` once per [key] until [forget] - the caller that claims it owns resolution. */
        fun claim(key: String): Boolean = claimed.add(key)

        /** `true` when [peer] differs from the endpoint last published for [key]. */
        fun accept(key: String, peer: MulticastDnsPeer): Boolean =
            published.put(key, peer) != peer

        /** Registers teardown for [key], run by [forget] or [clear]. */
        fun onForget(key: String, task: AwaitCloseTask) {
            cleanups[key] = task
        }

        /** Returns the peer that was published for [key], or null when there was none. */
        fun forget(key: String): MulticastDnsPeer? {
            claimed.remove(key)
            cleanups.remove(key)?.invoke()
            return published.remove(key)
        }

        fun clear() {
            Timber.v("Clearing registry: %d claimed, %d cleanups", claimed.size, cleanups.size)

            claimed.clear()
            published.clear()

            cleanups.forEach { (_, cleanup) -> cleanup() }
            cleanups.clear()
        }
    }

    private class DiscoveryListener(
        private val nsdManager: NsdManager,
        private val scanStarted: () -> Unit,
        private val scanStopped: () -> Unit,
        private val scanError: (Int) -> Unit,
        private val serviceFound: (NsdServiceInfo) -> Unit,
        private val serviceLost: (NsdServiceInfo) -> Unit,
    ) : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
            scanError(errorCode)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            // No second stopServiceDiscovery here: NSD just refused one, and asking again is how
            // this callback ends up firing itself in a loop.
            scanError(errorCode)
        }

        override fun onDiscoveryStarted(regType: String) {
            scanStarted()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            scanStopped()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            serviceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            serviceLost(serviceInfo)
        }
    }
}

/**
 * De-dup key for this service, or `null` when it is not one of app-defined.
 *
 * Instance name is unique per peer within a type, and survives address changes - keying on the
 * resolved address instead would re-report a peer that moved to another subnet.
 */
private fun NsdServiceInfo.matchedServiceKey(): String? {
    val type = serviceType?.trim('.')?.lowercase() ?: return null
    if (!type.contains(SERVICE_TYPE_TOKEN)) return null

    val name = serviceName?.takeIf { it.isNotBlank() } ?: return null

    return "$SERVICE_TYPE_TOKEN/$name"
}

/**
 * Map [NsdServiceInfo] to [MulticastDnsPeer], or `null` if it has no usable endpoint
 */
private fun NsdServiceInfo.toPeer(key: String): MulticastDnsPeer? {
    val host = usableHost() ?: return null
    if (port !in VALID_PORT_RANGE) return null

    return MulticastDnsPeer(
        id = key,
        name = serviceName.orEmpty(),
        host = host,
        port = port,
        attributes = attributes.orEmpty().asAttributes(),
    )
}

/**
 * Find usable host for [NsdServiceInfo].
 * Prefers IPv4 over IPv6, and filters out loopback, link-local, and any-local addresses.
 */
private fun NsdServiceInfo.usableHost(): String? {
    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        hostAddresses
    } else {
        @Suppress("DEPRECATION")
        listOfNotNull(host)
    }

    return addresses
        .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress || it.isLinkLocalAddress }
        .minByOrNull { if (it is Inet4Address) 0 else 1 }
        ?.hostAddress
        ?.takeIf { it.isNotBlank() }
}
