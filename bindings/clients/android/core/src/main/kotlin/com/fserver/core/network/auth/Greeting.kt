package com.fserver.core.network.auth

import com.fserver.net.peer.PublicGreeting

/**
 * What a device is willing to say before anyone authenticates: which protocol versions it speaks
 * and which of the methods it offers this build recognizes.
 *
 * **Advisory only.** Nothing here is verified.
 * Method this build does not recognize is left out rather than guessed at.
 */
data class Greeting(
    val protocolVersions: IntRange,
    val methods: List<AuthMethod>,
) {
    companion object {
        internal fun fromNetworkGreeting(public: PublicGreeting) = Greeting(
            protocolVersions = public.protocolVersions,
            methods = public.methods.mapNotNull { AuthMethod.fromId(it) },
        )
    }
}
