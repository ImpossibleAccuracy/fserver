package com.fserver.net.wire

/**
 * Version of the `:net` wire protocol - the envelope layout and the handshake. Independent of the
 * dictionary version, which the user negotiates separately.
 */
internal object ProtocolVersions {
    const val CURRENT: Int = 1

    val SUPPORTED: IntRange = 1..1
}
