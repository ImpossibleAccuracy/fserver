package com.fserver.app.domain

import kotlinx.coroutines.flow.Flow

interface AuthManager {
    val profile: Flow<Profile?>

    suspend fun login(name: String)

    suspend fun ensureLoggedIn()

    data class Profile(
        val name: String,
    )
}
