package com.fserver.core.domain.model.connection.device

/**
 * What sort of machine a peer says it is. Advisory: it comes from the peer's own description, and
 * nothing about it is proven by the handshake.
 */
enum class DeviceKind(
    internal val serialized: String,
) {
    Desktop("desktop"),
    Laptop("laptop"),
    Phone("phone"),
    Tablet("tablet"),
    Nas("nas");

    companion object {
        internal fun fromSerialized(serialized: String?): DeviceKind? {
            serialized ?: return null
            return entries.firstOrNull { it.serialized == serialized }
        }
    }
}
