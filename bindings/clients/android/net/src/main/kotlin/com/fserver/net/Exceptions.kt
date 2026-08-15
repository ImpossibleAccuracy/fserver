package com.fserver.net

import com.fserver.net.dictionary.MessageDictionary
import kotlin.time.Duration

/** Everything `:net` throws. Hosts can catch this one type and still tell the cases apart. */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The bytes on the wire were not what the protocol says they should be. */
    class Protocol(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** No transport in the node can reach the endpoint that was asked for. */
    class NoRoute(message: String) : NetworkException(message)

    /** The transport refused, timed out, or died while opening. */
    class Transport(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** The handshake did not complete: versions, identity, or the dictionary. */
    open class Handshake(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** The peer speaks a dictionary this side declined - reported by the user's own `negotiate`. */
    class DictionaryMismatch(
        val remote: MessageDictionary.Descriptor,
        val reason: String,
    ) : Handshake("dictionary rejected: $reason (remote ${remote.id} v${remote.version})")

    /** The host's authenticator refused this peer. */
    class AuthenticationRejected(reason: String, cause: Throwable? = null) : Handshake("peer rejected: $reason", cause)

    /** The session is gone; open a new one instead of retrying on this object. */
    class SessionClosed(message: String = "session is closed") : NetworkException(message)

    /** The link dropped. Pending requests fail with this - `:net` never silently re-sends them. */
    class SessionLinkLost(cause: Throwable?) : NetworkException("session link lost", cause)

    /** No answer in time. Whether to ask again is the dictionary's decision, not the network's. */
    class RequestTimeout(timeout: Duration) : NetworkException("no response within $timeout")
}
