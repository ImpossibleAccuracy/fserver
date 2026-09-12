package com.fserver.net.security.identity


/** Provider for [LocalIdentity] instances, and the primitives an identity is proven with. */
interface IdentityStore {
    suspend fun local(): LocalIdentity

    /**
     * Signs with the private half of [local]'s identity key.
     * The key itself never leaves the store.
     */
    suspend fun sign(data: ByteArray): ByteArray

    /**
     * The other half of [sign]: checks a peer's proof of possession of [publicKey].
     *
     * @throws com.fserver.common.exception.NetworkException.AuthenticationRejected when the
     * signature does not hold, malformed input included.
     */
    suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray)
}
