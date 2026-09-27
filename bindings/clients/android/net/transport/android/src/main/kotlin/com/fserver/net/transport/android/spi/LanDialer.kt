package com.fserver.net.transport.android.spi

import android.net.Network
import com.fserver.net.connection.LanPorts
import com.fserver.net.transport.android.spi.multicastdns.SocketTuning
import com.fserver.net.transport.android.spi.multicastdns.closeQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration

/**
 * Dialling one host over the fixed port list, shared by every TCP transport here.
 *
 * A route is written down with the port that answered, but a peer that restarted may have taken
 * another port of [LanPorts.PREFERRED], which leaves that route pointing at nothing. Sweeping the
 * list is what gets the device back without a scan. See `Connection Protocol.md` §4.6.
 */
internal object LanDialer {
    /** Ports to try, in order: the one already known first, then the rest of the fixed list. */
    fun ports(known: Int?): List<Int> =
        if (known == null) LanPorts.PREFERRED
        else listOf(known) + (LanPorts.PREFERRED - known)

    /**
     * Connects to the first of [ports] that answers. The returned socket names it in `port`.
     *
     * The sweep goes on only while the host refuses: a refusal means the device is there and is
     * not listening on that port, while a timeout or an unreachable host means it is not on the
     * network at all, and trying the rest would only multiply the wait.
     *
     * @param network Pins the socket to it, past whatever VPN would otherwise carry it.
     */
    suspend fun connect(
        host: String,
        ports: List<Int>,
        timeout: Duration,
        network: Network? = null,
    ): Socket = withContext(Dispatchers.IO) {
        var failure: Throwable? = null

        for (port in ports) {
            currentCoroutineContext().ensureActive()

            val socket = Socket()
            SocketTuning.beforeConnect(socket)

            try {
                network?.bindSocket(socket)
                socket.connect(
                    /* endpoint = */ InetSocketAddress(host, port),
                    /* timeout = */ timeout.inWholeMilliseconds.toInt(),
                )
                // A connect that lands after the caller gave up would otherwise leak the socket.
                currentCoroutineContext().ensureActive()

                return@withContext socket
            } catch (e: CancellationException) {
                socket.closeQuietly()
                throw e
            } catch (t: Throwable) {
                socket.closeQuietly()
                failure = t
                if (t !is ConnectException) break
            }
        }

        throw failure ?: IOException("no port to dial on $host")
    }
}
