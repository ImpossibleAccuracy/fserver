package com.fserver.net.security.auth.pake

/**
 * Storage for the password used in PAKE authentication when the peer is in the Responder role.
 * Password should be set by the user and stored securely, before enabling PAKE authentication.
 */
interface PakePasswordStorage {
    val isPasswordSet: Boolean

    suspend fun loadSavedPassword(): String
}
