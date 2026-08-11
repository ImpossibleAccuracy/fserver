package com.fserver.net.dictionary

/** Turns messages into bytes and back. The only code in the process that knows the wire shape. */
interface MessageCodec<T : Any> {
    fun encode(message: T): ByteArray
    fun decode(bytes: ByteArray): T
}
