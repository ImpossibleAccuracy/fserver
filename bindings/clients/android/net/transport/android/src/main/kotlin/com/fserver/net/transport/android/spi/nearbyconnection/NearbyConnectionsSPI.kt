package com.fserver.net.transport.android.spi.nearbyconnection

import android.content.Context
import com.fserver.net.security.IdentityStore
import com.fserver.net.spi.SpiContainer
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository

public data object NearbyConnectionsSPI {
    val ID: SpiId = SpiId("nearby-connections")

    public fun create(
        context: Context,
        config: Config,
        identityStore: IdentityStore,
    ): SpiContainer {
        val repository = NearbyConnectionsRepository(
            context = context,
            config = config,
        )

        val advertiser = NearbyConnectionsAdvertiser(repository)
        val discoveryProvider = NearbyConnectionsDiscoveryProvider(identityStore, repository)

        val transport = NearbyConnectionsTransport(repository)

        return SpiContainer(
            transport = transport,
            discoveryProvider = discoveryProvider,
            advertiser = advertiser,
            advertisedAttributes = emptyMap(),
        )
    }

    public data class Config(
        val serviceId: String,
    )
}
