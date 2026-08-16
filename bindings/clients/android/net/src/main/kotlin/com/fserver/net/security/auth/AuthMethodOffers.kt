package com.fserver.net.security.auth

import com.fserver.net.config.NetworkConfig
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportCapabilities

/**
 * Find the methods this config would offer over transport with [capabilities].
 */
internal fun NetworkConfig<*>.offeredMethods(
    capabilities: TransportCapabilities,
): List<AuthMethod> = authMethods
    .filter { it.isEnabled }
    .let { methods ->
        when (val security = capabilities.security) {
            null -> methods.filterNot { it.requiresChannelSecurity }
            else -> methods.filter { it.id == security }
        }
    }

/**
 * Whether a session carried by [transport] and authenticated with [method] is one this config would still admit.
 * False once either drops out - the transport is gone, or the method is no longer offered on it.
 */
internal fun NetworkConfig<*>.permits(transport: SpiId, method: AuthMethodId): Boolean {
    val carrier = transports.firstOrNull { it.id == transport } ?: return false
    return offeredMethods(carrier.capabilities).any { it.id == method }
}

/**
 * Which methods are worth putting on the air. A transport-backed one is announced only if some
 * installed transport actually backs it - promising `nearby-sas` over Wi-Fi would be a claim this
 * node cannot honor.
 */
internal fun NetworkConfig<*>.advertisableMethods(): List<AuthMethodId> {
    val backed = transports.mapNotNullTo(mutableSetOf()) { it.capabilities.security }
    return authMethods
        .filter { it.isEnabled && (!it.requiresChannelSecurity || it.id in backed) }
        .map { it.id }
}
