package com.fserver.net.transport.android.spi.multicastdns

import timber.log.Timber
import java.net.ServerSocket
import java.net.Socket

/**
 * What the kernel is told about a link before bytes move over it.
 *
 * The two buffers are sized for opposite reasons, and getting them the same is what made a
 * transfer slow here: the receive buffer caps the window a peer may fill, so it wants to be
 * generous, while the send buffer is a queue every later frame waits behind, so it wants to be
 * barely larger than the link can hold in flight.
 *
 * The kernel is free to grant something other than what it is asked for - it doubles a request,
 * and clamps it to its own maximum - which is why every setter here is followed by a read back.
 */
internal object SocketTuning {
    /**
     * How much unsent data the kernel may hold for us.
     *
     * Deliberately small. A send buffer only has to cover the bandwidth-delay product to keep the
     * link full; everything past that is a queue, and a queue that stays full delays every frame
     * behind it by the time it takes to drain. Measured here: an idle round trip is under 35ms
     * (`init-rtt`) at ~3.5 MiB/s, so the product is ~120KB - while a 4 MiB buffer, which is what
     * the kernel granted when we asked for 2, stood at over a second and put that second in front
     * of every `UploadCompleted`.
     */
    private const val SendBufferBytes: Int = 256 * 1024

    /**
     * What we let the peer keep in flight toward us.
     *
     * Large on purpose, and not the mirror of [SendBufferBytes]: a reception buffer is a ceiling on
     * the window we advertise, not a queue we make anyone wait behind. Sized past any LAN's
     * bandwidth-delay product so it never becomes the limit.
     */
    private const val ReceiveBufferBytes: Int = 2 * 1024 * 1024

    /**
     * Must be called before `connect`: the reception window is what gets advertised in the SYN, and
     * window scaling is negotiated there or not at all. Setting it afterward raises the buffer
     * but cannot raise the scale factor the handshake settled on.
     */
    fun beforeConnect(socket: Socket) {
        runCatching { socket.receiveBufferSize = ReceiveBufferBytes }
            .onFailure { Timber.w(it, "Cannot set the receive buffer") }
    }

    /**
     * Same, for the accepting side: what is set here is inherited by every accepted socket.
     *
     * `reuseAddress` because the listener wants a fixed port back: without it a connection left in
     * `TIME_WAIT` by the previous run keeps that port for minutes and pushes this one onto the
     * fallback. It does not let two live listeners share a port, so a real collision still shows.
     */
    fun beforeBind(server: ServerSocket) {
        runCatching { server.receiveBufferSize = ReceiveBufferBytes }
            .onFailure { Timber.w(it, "Cannot set the listener's receive buffer") }

        runCatching { server.reuseAddress = true }
            .onFailure { Timber.w(it, "Cannot set SO_REUSEADDR on the listener") }
    }

    /**
     * Applied once a socket is connected, and logs what the kernel actually granted.
     *
     * `tcpNoDelay` because frames are written whole and flushed: Nagle can only hold the last
     * partial segment of one back, waiting for an ack that the peer is delaying in turn.
     */
    fun afterConnect(socket: Socket) {
        runCatching { socket.tcpNoDelay = true }
            .onFailure { Timber.w(it, "Cannot disable Nagle") }

        runCatching { socket.sendBufferSize = SendBufferBytes }
            .onFailure { Timber.w(it, "Cannot set the send buffer") }

        runCatching {
            Timber.i(
                "socket ${socket.inetAddress?.hostAddress}:${socket.port} tuned: " +
                        "sndbuf ${socket.sendBufferSize}B (asked $SendBufferBytes), " +
                        "rcvbuf ${socket.receiveBufferSize}B (asked $ReceiveBufferBytes), " +
                        "tcpNoDelay ${socket.tcpNoDelay}"
            )
        }
    }
}
