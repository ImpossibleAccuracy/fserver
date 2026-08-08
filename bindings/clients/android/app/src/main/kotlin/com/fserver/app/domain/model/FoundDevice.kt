package com.fserver.app.domain.model

/**
 * A peer discovery turned up, whatever method found it.
 *
 * [kind] and [access] answer different questions and neither implies the other: [kind] is
 * what the box is, [access] is what the server demands before it will talk. A NAS may be
 * wide open and a laptop may want a password.
 *
 * Both are claims the peer makes before any trust exists, so they may only drive
 * presentation — never authorization. The server decides what it lets through.
 */
data class FoundDevice(
    val id: String,
    val name: String,
    val kind: Kind,
    val access: Access,
    val source: Source,
) {
    /** What the box is. Cosmetic: it picks the icon and the wording, nothing else. */
    enum class Kind {
        Desktop,
        Laptop,
        Phone,
        Tablet,
        Nas,
        Unknown,
    }

    /**
     * How the server gates connections (spec §3.2).
     *
     * The spec's "access by QR" is deliberately absent: that describes whether the server
     * publishes itself on the network, not what the client has to supply. Once its address
     * is known — scanned or typed — such a server is still [Open], [Password] or [Key].
     */
    enum class Access {
        Open,
        Password,
        Key,
    }

    sealed interface Source {
        data class NetworkServiceDiscovery(
            val serviceName: String,
            val serviceType: String,
            val domain: String,
        ) : Source

        data class SubnetScan(
            val ipAddress: String,
            val port: Int,
        ) : Source

        /**
         * The user supplied the address, by typing it or by scanning a code that carried it.
         * Both land here: a scanned address is still an address the user vouched for, and it
         * buys no trust the typed one does not have.
         */
        data class ManualEntry(
            val ipAddress: String,
            val port: Int,
        ) : Source

        data class NearbyDevice(
            val deviceId: String,
        ) : Source
    }
}
