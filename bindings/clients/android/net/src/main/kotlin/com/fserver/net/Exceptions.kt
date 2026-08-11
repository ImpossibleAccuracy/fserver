package com.fserver.net

import com.fserver.net.dictionary.DictionaryDescriptor
import kotlin.time.Duration

/** Everything `:net` throws. Hosts can catch this one type and still tell the cases apart. */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The bytes on the wire were not what the protocol says they should be. */
class ProtocolException(message: String, cause: Throwable? = null) : NetworkException(message, cause)

/** No transport in the node can reach the endpoint that was asked for. */
class NoRouteException(message: String) : NetworkException(message)

/** The transport refused, timed out, or died while opening. */
class TransportException(message: String, cause: Throwable? = null) : NetworkException(message, cause)

/** The handshake did not complete: versions, identity, or the dictionary. */
open class HandshakeException(message: String, cause: Throwable? = null) : NetworkException(message, cause)

/** The peer speaks a dictionary this side declined - reported by the user's own [negotiate]. */
class DictionaryMismatchException(
    val remote: DictionaryDescriptor,
    val reason: String,
) : HandshakeException("dictionary rejected: $reason (remote ${remote.id} v${remote.version})")

/** The host's authenticator refused this peer. */
class AuthenticationRejectedException(reason: String) : HandshakeException("peer rejected: $reason")

/** The session is gone; open a new one instead of retrying on this object. */
class SessionClosedException(message: String = "session is closed") : NetworkException(message)

/** The link dropped. Pending requests fail with this - `:net` never silently re-sends them. */
class SessionLinkLostException(cause: Throwable?) : NetworkException("session link lost", cause)

/** No answer arrived in time. Whether to ask again is the dictionary's decision, not the network's. */
class RequestTimeoutException(timeout: Duration) : NetworkException("no response within $timeout")
