package com.fserver.net.transport.android.spi.nearbyconnection

import android.content.Context
import com.fserver.net.config.SpiContainer
import com.fserver.net.security.IdentityStore
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository

public data object NearbyConnectionsSPI {
    public val ID: SpiId = SpiId("nearby-connections")

    public fun create(
        context: Context,
        config: Config,
        identityStore: IdentityStore,
    ): SpiContainer {
        val repository = NearbyConnectionsRepository(
            context = context.applicationContext,
            config = config,
            identityStore = identityStore,
        )

        return SpiContainer(
            transport = NearbyConnectionsTransport(repository),
            discoveryProvider = NearbyConnectionsDiscoveryProvider(repository),
            advertiser = NearbyConnectionsAdvertiser(repository),
            advertisedAttributes = emptyMap(),
        )
    }

    /** @param serviceId Both sides must use the same value, or they never see each other. */
    public data class Config(
        val serviceId: String,
    )
}
