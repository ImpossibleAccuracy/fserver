package com.fserver.core.network

import com.fserver.core.FServerConfig
import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.dictionary.FileServerDictionary
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.impl.IdentityStoreAdapter
import com.fserver.core.network.impl.TimberNetLogger
import com.fserver.core.network.impl.TrustStoreAdapter
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.network.AuthSettingsStore
import com.fserver.net.NetworkNode
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.networkConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.auth.sas.SasAuthMethod
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.crypto.X25519CryptoProvider
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the [NetworkNode]: the only class in `:core` that touches one. Exposes the slices `:core`
 * needs and keeps the node's auth methods in step with [AuthSettingsStore].
 */
internal class NetworkController(
    private val config: FServerConfig,
    private val storage: FServerStorage,
    private val authenticator: PeerAuthenticator,
    private val networkInfoRepository: NetworkInfoRepository,
    private val coroutineScope: BackgroundScope,
) {
    private val crypto = X25519CryptoProvider

    private val baseConfig: NetworkConfig<FileServerMessages> = buildBaseConfig()
    private val node: NetworkNode<FileServerMessages>

    private val hotSwapLock = Mutex()
    private var settingsWatcherJob: Job? = null

    /** Seeded from the value [baseConfig] was built with, so the initial emission is not a change. */
    private var lastOfferedMethods: List<OfferedAuthMethod>?

    val peerDiscovery get() = node.discovery
    val incomingConnections get() = node.incoming
    val requestManager get() = node.requestsManager

    init {
        val offered = storage.auth.offeredMethods.value
        lastOfferedMethods = offered
        node = NetworkNode.create(baseConfig.copy(authMethods = netAuthMethods(offered)))

        settingsWatcherJob = coroutineScope.launch { watchAuthSettings() }
    }

    suspend fun shutdown() {
        settingsWatcherJob?.cancelAndJoin()
        node.shutdown()
    }

    /** Swaps the node's auth methods whenever [AuthSettingsStore] reports a different set. */
    private suspend fun watchAuthSettings() {
        storage.auth.offeredMethods.collectLatest { offered ->
            hotSwapLock.withLock {
                if (lastOfferedMethods == offered) return@withLock
                lastOfferedMethods = offered

                try {
                    node.reloadConfig(
                        baseConfig.copy(authMethods = netAuthMethods(offered))
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The node keeps serving the previous config - a half-applied swap would be
                    // worse than a stale one. Clear the marker so the next emission retries.
                    TimberNetLogger.error("auth method reload failed; keeping previous config", e)
                    lastOfferedMethods = null
                }
            }
        }
    }

    private fun netAuthMethods(offered: List<OfferedAuthMethod>) = offered.map { method ->
        when (method) {
            OfferedAuthMethod.ConfirmFingerprint -> SasAuthMethod(
                crypto = crypto,
                confirmationCodeLength = InteractivePeerAuthenticator.GroupSize * 2,
            )

            is OfferedAuthMethod.Password -> PakeAuthMethod(
                crypto = crypto,
                loadSavedPassword = { method.password },
            )
        }
    }

    private fun buildBaseConfig(): NetworkConfig<FileServerMessages> =
        networkConfig(dictionary = FileServerDictionary()) {
            identityStore = IdentityStoreAdapter(storage.identity)
            trustStore = TrustStoreAdapter(
                trustedDevicesStore = storage.trust,
                networkInfoRepository = networkInfoRepository,
                timeProvider = config.timeProvider,
            )
            authenticator = null // this@NetworkController.authenticator
            crypto = PassthroughCryptoProvider // this@NetworkController.crypto
            scope = this@NetworkController.coroutineScope
            logger = TimberNetLogger
            policy = ConnectionPolicy(
                transportOrder = listOf(
                    NearbyConnectionsSPI.ID,
                    MulticastDnsSPI.ID,
                    DirectIpSPI.ID,
                )
            )

            install(
                DirectIpSPI.create(),
                MulticastDnsSPI.create(config.context),
                NearbyConnectionsSPI.create(
                    context = config.context,
                    config = NearbyConnectionsSPI.Config(
                        serviceId = "_fserver._tcp.",
                    ),
                ),
            )
        }
}
