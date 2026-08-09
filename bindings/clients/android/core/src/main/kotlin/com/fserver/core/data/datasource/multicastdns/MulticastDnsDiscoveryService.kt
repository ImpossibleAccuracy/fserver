package com.fserver.core.data.datasource.multicastdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.fserver.core.domain.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_TYPE = "_fserver._tcp."

/**
 * [SERVICE_TYPE] in the shape comparisons are made in: dots trimmed, case folded.
 *
 * NSD hands the type back in several forms - `_fserver._tcp`, `_fserver._tcp.local.`,
 * `_sub._fserver._tcp.` - so filtration is a substring test against this token rather than equality.
 */
private val SERVICE_TYPE_TOKEN = SERVICE_TYPE.trim('.').lowercase()

private typealias AwaitCloseTask = () -> Unit

internal class MulticastDnsDiscoveryService(
    private val context: Context,
) {
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val executor by lazy { Dispatchers.IO.asExecutor() }

    /**
     * Starts the mDNS discovery.
     *
     * @return flow of [MulticastDnsEvent] representing the mDNS events.
     */
    fun start(): Flow<MulticastDnsEvent> = callbackFlow {
        send(MulticastDnsEvent.Idle)

        val registry = ServiceRegistry()

        val discoveryListener = DiscoveryListener(
            nsdManager = nsdManager,
            scanStarted = {
                Timber.d("Discovery started for %s", SERVICE_TYPE)
                trySend(MulticastDnsEvent.Scanning)
            },
            scanStopped = {
                Timber.d("Discovery stopped for %s", SERVICE_TYPE)
                trySend(MulticastDnsEvent.Closed)
                close()
            },
            scanError = { error ->
                Timber.w("Discovery failed for %s, error=%d", SERVICE_TYPE, error)
                trySend(MulticastDnsEvent.Error(error))
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
                            trySend(MulticastDnsEvent.PeerError(key, errorCode))
                        },
                        onResolved = { service ->
                            Timber.d("Resolved %s at %s:%d", key, service.host, service.port)
                            trySend(MulticastDnsEvent.Found(service))
                        },
                        onForgot = {
                            trySend(MulticastDnsEvent.Disconnected(key))
                        },
                    )
                }
            },
            serviceLost = { serviceInfo ->
                // Drop the de-dup entry so a peer that leaves and comes back is reported again.
                serviceInfo.matchedServiceKey()?.let { key ->
                    Timber.d("Service lost: %s", key)
                    registry.forget(key)
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

            nsdManager.stopServiceDiscovery(discoveryListener)
            registry.clear()
        }
    }

    private fun resolveMdnsService(
        key: String,
        serviceInfo: NsdServiceInfo,
        registry: ServiceRegistry,
        onError: (Int) -> Unit,
        onResolved: (MulticastDnsPeer) -> Unit,
        onForgot: () -> Unit,
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

                    registry.forget(key)
                    onForgot()
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

        fun forget(key: String) {
            claimed.remove(key)
            published.remove(key)
            cleanups.remove(key)?.invoke()
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
            nsdManager.stopServiceDiscovery(this)
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
    if (port !in Constants.VALID_PORT_RANGE) return null

    return MulticastDnsPeer(
        id = key,
        name = serviceName.orEmpty(),
        host = host,
        port = port,
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
