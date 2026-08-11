package com.fserver.net.wire

import com.fserver.net.NetworkException

/**
 * Frame types. [isControl] frames jump the send queue: a multi-hour transfer must not sit in
 * front of a keep-alive or a close.
 */
enum class FrameKind(val code: Int) {
    HELLO(1),
    HELLO_ACK(2),
    READY(3),

    MESSAGE(10),
    REQUEST(11),
    RESPONSE(12),
    ERROR(13),

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