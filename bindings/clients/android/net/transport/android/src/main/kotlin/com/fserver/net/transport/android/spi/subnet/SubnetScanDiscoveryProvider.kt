package com.fserver.net.transport.android.spi.subnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.fserver.net.connection.LanPorts
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.LanDialer
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.multicastdns.closeQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.Inet4Address
import java.net.Socket

/**
 * One-shot sweep: the flow completes once every host was tried. A hit proves only that something
 * listens on [LanPorts.PREFERRED] - nothing is read off the wire, the handshake names the device.
 *
 * Only Wi-Fi and Ethernet networks are swept, with every socket pinned to its network: a VPN
 * accepts any TCP connect itself before trying the real host, so through it every address answers.
 */
internal class SubnetScanDiscoveryProvider(context: Context) : DiscoveryProvider {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override val id: SpiId = SubnetScanSPI.ID

    override fun accepts(params: DiscoveryProvider.ScanParams): Boolean =
        params is SubnetScanParams

    override fun scan(params: DiscoveryProvider.ScanParams): Flow<DiscoveryProvider.Event> {
        require(params is SubnetScanParams) { "$id cannot serve $params" }

        return channelFlow {
            val targets = lanTargets()
            if (targets.isEmpty()) {
                send(DiscoveryProvider.Event.Failed(IOException("no Wi-Fi or Ethernet subnet to scan")))
                return@channelFlow
            }

            val permits = Semaphore(params.parallelism)
            for ((network, hosts) in targets) {
                if (!canBind(network)) {
                    // An always-on VPN that forbids bypassing it: every probe would be answered by it.
                    send(DiscoveryProvider.Event.Failed(IOException("cannot reach $network past the VPN")))
                    continue
                }

                hosts.forEach { host ->
                    launch {
                        permits.withPermit {
                            probe(network, host, params)?.let { send(DiscoveryProvider.Event.Appeared(it)) }
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Hosts per LAN network, minus this device's own addresses on it. */
    @Suppress("DEPRECATION") // allNetworks: the callback replacement is overkill for a one-shot read.
    private fun lanTargets(): List<Pair<Network, List<String>>> = connectivity.allNetworks.mapNotNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
        val isLan = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!isLan || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null

        val addresses = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address }
        val own = addresses.mapTo(mutableSetOf()) { it.address.hostAddress }
        val hosts = addresses
            .flatMap { SubnetHosts.hosts(it.address as Inet4Address, it.prefixLength) }
            .distinct()
            .filterNot { it in own }

        if (hosts.isEmpty()) null else network to hosts
    }

    private fun canBind(network: Network): Boolean = try {
        Socket().use { network.bindSocket(it) }
        true
    } catch (e: IOException) {
        false
    }

    /** Null when nothing answers - the normal case for most of a subnet, so not a failure. */
    private suspend fun probe(network: Network, host: String, params: SubnetScanParams): DiscoveredEndpoint? {
        val socket = try {
            LanDialer.connect(host, LanPorts.PREFERRED, params.connectTimeout, network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        socket.closeQuietly()

        return DiscoveredEndpoint(
            endpoint = DirectIpEndpoint(host = host, port = socket.port),
            advertisedName = host,
        )
    }
}
