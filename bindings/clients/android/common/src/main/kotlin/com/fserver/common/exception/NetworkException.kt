package com.fserver.common.exception

import kotlin.time.Duration


sealed class NetworkException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    /** The bytes on the wire were not what the protocol says they should be. */
    class Protocol(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** No transport in the node can reach the endpoint that was asked for. */
    class NoRoute(message: String) : NetworkException(message)

    /** The transport refused, timed out, or died while opening. */
    class Transport(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** The handshake did not complete: versions, identity, or the dictionary. */
    open class Handshake(message: String, cause: Throwable? = null) :
        NetworkException(message, cause)

    /** The host's authenticator refused this peer. */
    class AuthenticationRejected(reason: String, cause: Throwable? = null) :
        Handshake("peer rejected: $reason", cause)

    /** The session is gone; open a new one instead of retrying on this object. */
    class SessionClosed(message: String = "session is closed") : NetworkException(message)

    /** The link dropped. Pending requests fail with this - `:net` never silently re-sends them. */
    class SessionLinkLost(cause: Throwable?) : NetworkException("session link lost", cause)

    /** No answer in time. Whether to ask again is the dictionary's decision, not the network's. */
    class RequestTimeout(timeout: Duration) : NetworkException("no response within $timeout")

    /**
     * The message is past what this link will carry at all.
     *
     * Not the same as "does not fit a frame" - a message over the frame size is split and sent in
     * pieces. This is the ceiling on the whole message.
     */
    class FrameTooLarge(val size: Int, val limit: Int) :
        NetworkException("message of $size bytes exceeds the $limit byte payload limit of this link")
}
