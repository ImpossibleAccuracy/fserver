package com.fserver.net.wire

/**
 * One slice of a message too large for a single frame.
 *
 * The slice travels as the payload of a [FrameKind.CHUNK] envelope, which keeps the message id and
 * correlation id of the message being split - that is what lets slices of two messages interleave
 * on the wire and still be told apart. [kind] is the kind the message is rebuilt as, so nothing
 * above the session ever learns a message arrived in pieces.
 */
internal class Fragment(
    val kind: FrameKind,
    val index: Int,
    val total: Int,
    val part: ByteArray,
) {
    object Codec {
        /** What a slice costs on top of its bytes: [kind], [index], [total]. */
        const val HEADER_SIZE = 1 + 4 + 4

        fun encode(fragment: Fragment): ByteArray = ByteWriter(HEADER_SIZE + fragment.part.size)
            .u8(fragment.kind.code)
            .i32(fragment.index)
            .i32(fragment.total)
            // Last field, so it needs no length of its own - the frame already carries one.
            .raw(fragment.part)
            .toByteArray()

        fun decode(payload: ByteArray): Fragment {
            val reader = ByteReader(payload)
            return Fragment(
                kind = FrameKind.from(reader.u8()),
                index = reader.i32(),
                total = reader.i32(),
                part = reader.rest(),
            )
        }
    }
}
