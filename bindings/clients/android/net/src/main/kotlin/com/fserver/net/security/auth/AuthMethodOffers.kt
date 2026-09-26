package com.fserver.net.security.auth

import com.fserver.net.config.NetworkConfig
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportCapabilities

/** Whether peers may use [method] to reach this node. */
internal fun NetworkConfig<*>.isOffered(method: AuthMethod): Boolean =
    offeredMethodIds?.contains(method.id) ?: true

/**
 * Find the methods this config can run over transport with [capabilities], offered or not.
 * What this side dials with is its own choice; only what it answers to is policy.
 */
internal fun NetworkConfig<*>.runnableMethods(
    capabilities: TransportCapabilities,
): List<AuthMethod> = when (val security = capabilities.security) {
    null -> authMethods.filterNot { it.requiresChannelSecurity }
    else -> authMethods.filter { it.id == security }
}

/**
 * Find the methods this config would offer over transport with [capabilities].
 */
internal fun NetworkConfig<*>.offeredMethods(
    capabilities: TransportCapabilities,
): List<AuthMethod> = runnableMethods(capabilities).filter { isOffered(it) }

/**
 * Whether a session carried by [transport] and authenticated with [method] is one this config would still admit.
 * False once either drops out - the transport is gone, or the method is no longer usable on it.
 * A [dialled] session is held only to what this side can run, not to what it offers.
 */
internal fun NetworkConfig<*>.permits(transport: SpiId, method: AuthMethodId, dialled: Boolean): Boolean {
    val carrier = transports.firstOrNull { it.id == transport } ?: return false
    val usable = if (dialled) runnableMethods(carrier.capabilities) else offeredMethods(carrier.capabilities)
    return usable.any { it.id == method }
}

/**
 * Which methods are worth putting on the air. A transport-backed one is announced only if some
 * installed transport actually backs it - promising `nearby-sas` over Wi-Fi would be a claim this
 * node cannot honor.
 */
internal fun NetworkConfig<*>.advertisableMethods(): List<AuthMethodId> {
    val backed = transports.mapNotNullTo(mutableSetOf()) { it.capabilities.security }
    return authMethods
        .filter { isOffered(it) && (!it.requiresChannelSecurity || it.id in backed) }
        .map { it.id }
}
