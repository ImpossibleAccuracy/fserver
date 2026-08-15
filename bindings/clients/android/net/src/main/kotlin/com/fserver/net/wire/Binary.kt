package com.fserver.net.wire

import com.fserver.net.NetworkException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Minimal big-endian writer. Everything `:net` puts on the wire is built with this. */
class ByteWriter(initialCapacity: Int = 64) {
    private var buffer = ByteBuffer.allocate(initialCapacity)

    fun u8(value: Int) = apply { ensure(1); buffer.put(value.toByte()) }

    fun i32(value: Int) = apply { ensure(4); buffer.putInt(value) }

    fun i64(value: Long) = apply { ensure(8); buffer.putLong(value) }

    fun bool(value: Boolean) = u8(if (value) 1 else 0)

    fun bytes(value: ByteArray) = apply {
        ensure(4 + value.size)
        buffer.putInt(value.size)
        buffer.put(value)
    }

    fun string(value: String) = bytes(value.toByteArray(StandardCharsets.UTF_8))

    fun raw(value: ByteArray) = apply { ensure(value.size); buffer.put(value) }

    fun toByteArray(): ByteArray = buffer.array().copyOf(buffer.position())

    private fun ensure(extra: Int) {
        if (buffer.remaining() >= extra) return
        val grown = ByteBuffer.allocate(maxOf(buffer.capacity() * 2, buffer.position() + extra))
        grown.put(buffer.array(), 0, buffer.position())
        buffer = grown
    }
}

/** Matching reader. Every malformed input surfaces as [NetworkException.Protocol], never as a raw buffer error. */
class ByteReader(source: ByteArray) {
    private val buffer = ByteBuffer.wrap(source)

    val remaining: Int get() = buffer.remaining()

    fun u8(): Int = guard { buffer.get().toInt() and 0xFF }

    fun i32(): Int = guard { buffer.int }

    fun i64(): Long = guard { buffer.long }

    fun bool(): Boolean = u8() != 0

    fun bytes(): ByteArray = guard {
        val length = buffer.int
        if (length < 0 || length > buffer.remaining()) {
            throw NetworkException.Protocol("declared length $length does not fit in ${buffer.remaining()} bytes")
        }
        ByteArray(length).also(buffer::get)
    }

    fun string(): String = String(bytes(), StandardCharsets.UTF_8)

    fun rest(): ByteArray = ByteArray(buffer.remaining()).also(buffer::get)

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: BufferUnderflowException) {
        throw NetworkException.Protocol("frame truncated", e)
    }
}
