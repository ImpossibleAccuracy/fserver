package com.fserver.net.dictionary

/**
 * The one place where a user of `:net` says what devices talk *about*.
 *
 * `:net` never looks inside a message: it carries [MessageCodec] output as an opaque byte array. The only
 * dictionary metadata it handles is [descriptor], which it exchanges during the handshake and
 * hands straight back to [negotiate] - the compatibility verdict is the user's, not the network's.
 *
 * One user, one dictionary: the type parameter is pinned to the whole
 * [com.fserver.net.NetworkNode].
 */
interface MessageDictionary<M : Any> {
    val descriptor: Descriptor

    val codec: MessageCodec<M>

    /** Called once per handshake, with what the other side announced. */
    fun negotiate(remote: Descriptor): Decision

    /**
     * @property id dictionaries must match by id; two devices speaking different vocabularies have
     * nothing to say to each other.
     * @property version what this side speaks now.
     * @property supported what it can still serve, for older peers.
     */
    data class Descriptor(
        val id: String,
        val version: Int,
        val supported: IntRange = version..version,
        val extras: Map<String, String> = emptyMap(),
    )

    sealed interface Decision {
        data class Accept(val effectiveVersion: Int) : Decision
        data class Reject(val reason: String) : Decision
    }
}
