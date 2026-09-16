package com.fserver.net.connection

/**
 * TCP ports a LAN listener takes, in order, before falling back to an ephemeral one.
 *
 * Fixed so a route written down survives a restart, and so a host known without a usable port - an
 * address typed in, an inbound route carrying the peer's source port - can be guessed at.
 *
 * Protocol, not an Android choice: the numbers are in `Connection Protocol.md` §4.6 and every
 * client and the server follow the same list.
 */
object LanPorts {
    // TODO: move to BuildConfig
    val PREFERRED: List<Int> = listOf(29470, 29471, 29472)

    /** What to dial when a host is known and a port is not. */
    val DEFAULT: Int = PREFERRED.first()
}
