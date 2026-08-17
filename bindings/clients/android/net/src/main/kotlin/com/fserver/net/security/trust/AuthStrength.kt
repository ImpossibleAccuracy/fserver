package com.fserver.net.security.trust

/**
 * How hard a method is to walk past, as a total order.
 * Pinned next to the peer's key, so a later connection offering something weaker is a downgrade rather than a choice.
 *
 * Declaration order is the comparison order, and it ranks what an attacker has to beat,
 * not how good the implementation is.
 */
enum class AuthStrength {
    /** The transport secured the link and this rides on it. Proves the channel, not the device. */
    ChannelBound,

    /** Ephemeral exchange bound to a secret both ends already held. */
    SharedSecret,

    /** Ephemeral exchange plus a short string a person compared out of band. */
    UserCompared,
}
