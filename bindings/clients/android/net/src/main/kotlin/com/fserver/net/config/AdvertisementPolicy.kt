package com.fserver.net.config

/**
 * What this device puts on the air about itself.
 *
 * Advertising is the one channel a device chooses to open: it can be turned off, and it never
 * answers a stranger who merely found the address. That is why a name is acceptable here and not
 * in the public greeting, which anyone who dialled gets an answer from.
 *
 * @property enabled false stops the device announcing itself at all. That is the whole mechanism
 * behind code-only access (ToR §3.2): a device nobody can find is not one a scan can name, and no
 * amount of care about individual fields substitutes for it.
 * @property publishName false trades the device list showing a name for saying even less.
 */
data class AdvertisementPolicy(
    val enabled: Boolean = true,
    val publishName: Boolean = true,
)
