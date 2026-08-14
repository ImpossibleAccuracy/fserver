package com.fserver.net.transport.android.datasource.nearbyconnection

import com.fserver.net.discovery.PeerAttributes
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyEndpointInfo.MAX_BYTES
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Packs the advertisement into the one field Nearby carries about an endpoint before it is
 * connected.
 *
 * Nearby has no TXT records and the endpoint info is capped at [MAX_BYTES], so this is binary
 * rather than text: what identifies a device goes in as raw bytes - a UUID is 16 bytes, not 36
 * characters - which keeps the identifying part a fixed ~70 bytes however many attributes `:net`
 * grows. Everything the encoding does not know about rides as a text record while there is room,
 * and is dropped when there is not.
 *
 * Dropping is safe by construction: attributes are hints for the UI, and the handshake
 * re-establishes and verifies the lot. The device id is the exception - without it `:net` falls
 * back to the endpoint id, which Nearby regenerates every advertising session, so the same device
 * shows up as a new one after every restart and never merges with its mDNS route.
 */
internal object NearbyEndpointInfo {

    /** Nearby's limit on what an endpoint can say about itself. */
    const val MAX_BYTES: Int = 131

    /** Long names crowd out everything else, and the UI truncates them anyway. */
    private const val MAX_DISPLAY_NAME_BYTES = 32

    /** The first byte of the encoding, so a peer that does not speak it can fall back to text. */
    private const val MAGIC = 'F'.code.toByte()
    private const val FORMAT_VERSION: Byte = 1
    private const val HEADER_BYTES = 2

    // Tags are the wire format. Add, never renumber - a peer on an older build skips what it does
    // not know by length and keeps the rest.
    private const val TAG_DEVICE_ID_UUID: Byte = 0x01
    private const val TAG_DEVICE_ID_TEXT: Byte = 0x02
    // 0x03 and 0x04 carried the key fingerprint. Retired: a stable identifier on the air is what
    // lets a passive listener follow a device between networks. Reserved, never reused.
    private const val TAG_DISPLAY_NAME: Byte = 0x05
    private const val TAG_PROTOCOL_MIN: Byte = 0x06
    private const val TAG_PROTOCOL_MAX: Byte = 0x07

    // 0x08 briefly carried the auth methods. Retired: this transport fixes its own method, so a
    // peer reads it off the transport rather than off the air. Reserved, never reused.

    /** Anything the encoding has no compact form for: `key=value`, UTF-8. */
    private const val TAG_ATTRIBUTE: Byte = 0x7F

    private const val ASSIGN = '='

    private const val UUID_BYTES = 16
    private const val FINGERPRINT_BYTES = 8
    private const val FINGERPRINT_GROUP = 4

    /**
     * [essential] is written first, in tag order, so a budget that runs out takes [optional] with
     * it and never the identity. Keys [essential] holds that have no compact form fall through to
     * text records, as does everything in [optional].
     */
    fun encode(
        essential: Map<String, String>,
        optional: Map<String, String> = emptyMap(),
    ): ByteArray {
        val out = ByteBuffer.allocate(MAX_BYTES)
        out.put(MAGIC)
        out.put(FORMAT_VERSION)

        essential[PeerAttributes.DEVICE_ID]?.let { deviceId ->
            val raw = deviceId.asUuidBytes()
            if (raw != null) {
                out.putRecord(TAG_DEVICE_ID_UUID, raw)
            } else {
                out.putRecord(TAG_DEVICE_ID_TEXT, deviceId.toByteArray(Charsets.UTF_8))
            }
        }

        essential[PeerAttributes.DISPLAY_NAME]?.let { name ->
            val capped = name.takeUtf8(MAX_DISPLAY_NAME_BYTES)
            if (capped.isNotEmpty()) {
                out.putRecord(TAG_DISPLAY_NAME, capped.toByteArray(Charsets.UTF_8))
            }
        }

        essential[PeerAttributes.PROTOCOL_MIN]?.asVersionByte()
            ?.let { out.putRecord(TAG_PROTOCOL_MIN, byteArrayOf(it)) }
        essential[PeerAttributes.PROTOCOL_MAX]?.asVersionByte()
            ?.let { out.putRecord(TAG_PROTOCOL_MAX, byteArrayOf(it)) }

        val text = LinkedHashMap<String, String>()
        essential.forEach { (key, value) -> if (key !in COMPACT_KEYS && key !in OMITTED) text[key] = value }
        optional.forEach { (key, value) -> if (key !in text && key !in OMITTED) text[key] = value }

        for ((key, value) in text) {
            if (key.isEmpty() || key.contains(ASSIGN)) continue
            out.putRecord(TAG_ATTRIBUTE, "$key$ASSIGN$value".toByteArray(Charsets.UTF_8))
        }

        return out.array().copyOf(out.position())
    }

    fun decode(raw: ByteArray): Decoded {
        if (raw.size < HEADER_BYTES || raw[0] != MAGIC || raw[1] != FORMAT_VERSION) {
            // A peer that does not speak this encoding - all it gave us is a name.
            return Decoded(displayName = raw.toString(Charsets.UTF_8), attributes = emptyMap())
        }

        val attributes = LinkedHashMap<String, String>()
        var at = HEADER_BYTES

        // Trailing garbage stops the walk rather than voiding what was read before it.
        while (at + 2 <= raw.size) {
            val tag = raw[at]
            val length = raw[at + 1].toInt() and 0xFF
            val from = at + 2
            if (from + length > raw.size) break

            val value = raw.copyOfRange(from, from + length)
            at = from + length

            when (tag) {
                TAG_DEVICE_ID_UUID ->
                    value.asUuidString()?.let { attributes[PeerAttributes.DEVICE_ID] = it }

                TAG_DEVICE_ID_TEXT ->
                    attributes[PeerAttributes.DEVICE_ID] = value.toString(Charsets.UTF_8)

                TAG_DISPLAY_NAME ->
                    attributes[PeerAttributes.DISPLAY_NAME] = value.toString(Charsets.UTF_8)

                TAG_PROTOCOL_MIN -> value.asVersionString()
                    ?.let { attributes[PeerAttributes.PROTOCOL_MIN] = it }

                TAG_PROTOCOL_MAX -> value.asVersionString()
                    ?.let { attributes[PeerAttributes.PROTOCOL_MAX] = it }

                TAG_ATTRIBUTE -> {
                    val record = value.toString(Charsets.UTF_8)
                    val split = record.indexOf(ASSIGN)
                    if (split > 0) {
                        attributes[record.take(split)] = record.substring(split + 1)
                    }
                }

                // A tag from a newer build. Its length already moved `at` past it.
                else -> Unit
            }
        }

        return Decoded(
            displayName = attributes[PeerAttributes.DISPLAY_NAME]
                ?: attributes[PeerAttributes.DEVICE_ID]
                ?: "",
            attributes = attributes,
        )
    }

    data class Decoded(
        val displayName: String,
        val attributes: Map<String, String>,
    )

    /**
     * Keys this transport never broadcasts. The auth method is fixed by the transport itself, so
     * announcing it would spend a scarce budget saying what the peer already knows from the
     * connection it is about to make.
     */
    private val OMITTED = setOf(PeerAttributes.AUTH_METHODS)

    /** Keys with a compact record of their own; everything else goes out as text. */
    private val COMPACT_KEYS = setOf(
        PeerAttributes.DEVICE_ID,
        PeerAttributes.DISPLAY_NAME,
        PeerAttributes.PROTOCOL_MIN,
        PeerAttributes.PROTOCOL_MAX,
    )

    /** Skips rather than stops: a long value should not hide the shorter ones behind it. */
    private fun ByteBuffer.putRecord(tag: Byte, value: ByteArray) {
        if (value.size > MAX_RECORD_VALUE || remaining() < value.size + 2) return

        put(tag)
        put(value.size.toByte())
        put(value)
    }

    private const val MAX_RECORD_VALUE = 0xFF

    private fun String.asUuidBytes(): ByteArray? {
        val uuid = try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            return null
        }
        // `UUID.toString` normalizes case and padding, so anything that does not survive the round
        // trip has to travel as text or the peer would see a different device id than we sent.
        if (uuid.toString() != this) return null

        return ByteBuffer.allocate(UUID_BYTES)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }

    private fun ByteArray.asUuidString(): String? {
        if (size != UUID_BYTES) return null

        val buffer = ByteBuffer.wrap(this)
        return UUID(buffer.long, buffer.long).toString()
    }

    /** `Fingerprint.of` is 8 bytes of SHA-256 as hex in groups of four - reversible either way. */
    private fun String.asFingerprintBytes(): ByteArray? {
        val hex = filterNot(Char::isWhitespace)
        if (hex.length != FINGERPRINT_BYTES * 2) return null
        // Anything not in that exact shape travels as text, or the peer would read back a
        // fingerprint that does not match the one this device shows on its own pairing screen.
        if (asFingerprintText(hex) != this) return null

        val bytes = ByteArray(FINGERPRINT_BYTES)
        for (index in 0 until FINGERPRINT_BYTES) {
            val byte =
                hex.substring(index * 2, index * 2 + 2).toIntOrNull(radix = 16) ?: return null
            bytes[index] = byte.toByte()
        }

        return bytes
    }

    private fun ByteArray.asVersionString(): String? =
        singleOrNull()?.let { (it.toInt() and 0xFF).toString() }

    private fun ByteArray.asFingerprintString(): String =
        asFingerprintText(joinToString("") { "%02x".format(it) })

    private fun asFingerprintText(hex: String): String =
        hex.chunked(FINGERPRINT_GROUP).joinToString(" ")

    private fun String.asVersionByte(): Byte? = toIntOrNull()?.takeIf { it in 0..0xFF }?.toByte()
}

private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

/** Truncates on a code point boundary, so a cut never produces half a surrogate pair. */
private fun String.takeUtf8(maxBytes: Int): String {
    if (utf8Size() <= maxBytes) return this

    var end = 0
    var used = 0
    while (end < length) {
        val codePoint = codePointAt(end)
        val size = String(Character.toChars(codePoint)).utf8Size()
        if (used + size > maxBytes) break

        used += size
        end += Character.charCount(codePoint)
    }

    return take(end)
}
