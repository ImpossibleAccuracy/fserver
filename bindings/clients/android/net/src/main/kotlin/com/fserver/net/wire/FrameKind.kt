package com.fserver.net.wire

import com.fserver.common.exception.NetworkException

/**
 * Frame types. [isControl] frames jump the send queue: a multi-hour transfer must not sit in
 * front of a keep-alive or a close.
 */
internal enum class FrameKind(val code: Int) {
    HELLO(1),
    HELLO_ACK(2),

    /** One round of the chosen [com.fserver.net.security.auth.AuthMethod]. Payload is opaque here. */
    AUTH(3),

    /**
     * Everything about a device that is not public: sent inside the sealed channel, once by each
     * side and in either order, because by then there is nothing to take turns over.
     */
    DESCRIPTOR(4),

    READY(5),

    MESSAGE(10),
    REQUEST(11),
    RESPONSE(12),
    ERROR(13),

    /**
     * One slice of a message that did not fit a frame. Carries the kind it will be rebuilt as,
     * so routing sees the whole message or nothing - see [com.fserver.net.wire.Fragment].
     */
    CHUNK(14),

    PING(20),
    PONG(21),
    CLOSE(22);

    val isControl: Boolean get() = code < 10 || code >= 20

    companion object {
        private val byCode = entries.associateBy(FrameKind::code)

        fun from(code: Int): FrameKind =
            byCode[code] ?: throw NetworkException.Protocol("unknown frame kind $code")
    }
}