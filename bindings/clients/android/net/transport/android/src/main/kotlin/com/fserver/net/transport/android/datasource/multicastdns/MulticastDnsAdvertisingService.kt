package com.fserver.net.transport.android.datasource.multicastdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Publishes this device as a [SERVICE_TYPE] service on the local network, so peers running
 * [MulticastDnsDiscoveryService] can find it.
 */
internal class MulticastDnsAdvertisingService(
    private val context: Context,
) {
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    /**
     * Starts the mDNS advertising.
     *
     * @return flow of [Event] representing the mDNS events.
     */
    fun start(
        identity: LocalIdentity,
        attributes: Map<String, String>,
        port: Int,
    ): Flow<Event> = callbackFlow {
        send(Event.Idle)

        val txtEntries = attributes.asTxtEntries()

        val serviceInfo = NsdServiceInfo().apply {
            // The instance name is what peers browse and what NSD renames on collision, so it
            // carries the display name; the device id travels in TXT, where it stays stable.
            serviceName = instanceName(identity.displayName)
            serviceType = SERVICE_TYPE
            this.port = port

            txtEntries.forEach { (key, value) -> setAttribute(key, value) }
        }

        // NSD rejects unregisterService for a listener it never registered, so teardown has to
        // know whether registration actually took.
        val registered = AtomicBoolean(false)

        val registrationListener = RegistrationListener(
            onRegistered = { service ->
                // NSD renames on name collision, so the published name may differ from the
                // requested one.
                Timber.d("Advertising as %s (%s)", service.serviceName, SERVICE_TYPE)

                registered.set(true)
                trySend(Event.Registered)
            },
            onUnregistered = {
                Timber.d("Advertising stopped for %s", SERVICE_TYPE)

                registered.set(false)
                trySend(Event.Closed)
                close()
            },
            onRegistrationFailed = { _, errorCode ->
                Timber.w("Registration failed for %s, error=%d", SERVICE_TYPE, errorCode)

                registered.set(false)
                trySend(Event.Error(errorCode))
                // Ends the flow: NSD will not retry this listener, so advertising only comes back
                // on another startAdvertising().
                close()
            },
            onUnregistrationFailed = { _, errorCode ->
                // The listener stays registered here, but teardown is already underway and
                // retrying would only leak another callback.
                Timber.w("Unregistration failed for %s, error=%d", SERVICE_TYPE, errorCode)

                trySend(Event.Error(errorCode))
                close()
            },
        )

        Timber.d("Starting mDNS advertising for %s", SERVICE_TYPE)

        nsdManager.registerService(
            /* serviceInfo = */ serviceInfo,
            /* protocolType = */ NsdManager.PROTOCOL_DNS_SD,
            /* listener = */ registrationListener,
        )

        awaitClose {
            Timber.d("Tearing down mDNS advertising for %s", SERVICE_TYPE)

            if (registered.compareAndSet(true, false)) {
                nsdManager.unregisterService(registrationListener)
            }
        }
    }

    internal sealed interface Event {
        data object Idle : Event
        data object Registered : Event
        data class Error(val errorCode: Int) : Event
        data object Closed : Event
    }

    private class RegistrationListener(
        private val onRegistered: (NsdServiceInfo) -> Unit,
        private val onUnregistered: (NsdServiceInfo) -> Unit,
        private val onRegistrationFailed: (NsdServiceInfo, Int) -> Unit,
        private val onUnregistrationFailed: (NsdServiceInfo, Int) -> Unit,
    ) : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
            onRegistrationFailed.invoke(service, errorCode)
        }

        override fun onServiceRegistered(service: NsdServiceInfo) {
            onRegistered(service)
        }

        override fun onServiceUnregistered(service: NsdServiceInfo) {
            onUnregistered(service)
        }

        override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {
            onUnregistrationFailed.invoke(service, errorCode)
        }
    }
}
