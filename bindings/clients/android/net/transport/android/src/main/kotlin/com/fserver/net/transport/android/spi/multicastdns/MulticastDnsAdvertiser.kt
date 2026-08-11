package com.fserver.net.transport.android.spi.multicastdns

import android.content.Context
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsAdvertisingService
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import java.io.IOException

/**
 * Advertises the port [MulticastDnsTransport] listens on, so nothing is published until that
 * listener runs. Both must be given the same [MulticastDnsPortBinder] instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MulticastDnsAdvertiser(
    private val context: Context,
    private val multicastDnsPortBinder: MulticastDnsPortBinder,
) : Advertiser {
    private val advertiserService = MulticastDnsAdvertisingService(
        context = context,
    )

    override val id: SpiId = MulticastDnsSPI.ID

    override fun advertise(
        payload: Advertiser.Payload
    ): Flow<Advertiser.Event> = multicastDnsPortBinder.value
        .flatMapLatest { port ->
            if (port == null) {
                Timber.w(
                    "Not advertising: no listening port yet. " +
                            "MulticastDnsTransport.listener.listen() has to be collected first.",
                )
                flowOf(MulticastDnsAdvertisingService.Event.Closed)
            } else {
                advertiserService.start(
                    identity = payload.identity,
                    // TXT records have room for the lot, so nothing is left behind here.
                    attributes = payload.attributes,
                    port = port,
                )
            }
        }
        .mapNotNull { advertiseEvent ->
            when (advertiseEvent) {
                MulticastDnsAdvertisingService.Event.Idle -> null

                MulticastDnsAdvertisingService.Event.Registered -> Advertiser.Event.Started

                is MulticastDnsAdvertisingService.Event.Error -> Advertiser.Event.Failed(
                    IOException(
                        "Multicast DNS advertising error code: ${advertiseEvent.errorCode}",
                    )
                )

                MulticastDnsAdvertisingService.Event.Closed -> Advertiser.Event.Stopped
            }
        }
}
