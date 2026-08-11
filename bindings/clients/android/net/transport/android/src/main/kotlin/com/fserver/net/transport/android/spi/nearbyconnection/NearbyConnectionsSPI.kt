package com.fserver.net.transport.android.spi.nearbyconnection

import android.content.Context
import com.fserver.net.config.SpiContainer
import com.fserver.net.config.SpiFactory
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository

public data object NearbyConnectionsSPI {
    public val ID: SpiId = SpiId("nearby-connections")

    public fun create(
        context: Context,
        config: Config,
    ): SpiFactory {
        val applicationContext = context.applicationContext

        return SpiFactory { environment ->
            val repository = NearbyConnectionsRepository(
                context = applicationContext,
                config = config,
                identityStore = environment.identityStore,
            )

            SpiContainer(
                transport = NearbyConnectionsTransport(repository),
                discoveryProvider = NearbyConnectionsDiscoveryProvider(repository),
                advertiser = NearbyConnectionsAdvertiser(repository),
                advertisedAttributes = emptyMap(),
            )
        }
    }

    /** @param serviceId Both sides must use the same value, or they never see each other. */
    public data class Config(
        val serviceId: String,
    )
}
