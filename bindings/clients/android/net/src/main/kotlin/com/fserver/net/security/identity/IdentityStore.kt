package com.fserver.net.security.identity


/** Provider for [LocalIdentity] instances. */
interface IdentityStore {
    suspend fun local(): LocalIdentity

    /**
     * Signs with the private half of [local]'s identity key.
     * The key itself never leaves the store.
     */
    suspend fun sign(data: ByteArray): ByteArray
}
