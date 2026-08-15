package com.fserver.core.domain.model.auth

/**
 * Available authentication methods.
 */
enum class AuthMethod {
    /** Ephemeral key agreement, confirmed by comparing a fingerprint on both screens. */
    ConfirmFingerprint,

    /** Nearby's own channel security, confirmed by comparing a short on-screen code. */
    NearbySas,

    /** Password-authenticated key exchange. */
    Password,
}