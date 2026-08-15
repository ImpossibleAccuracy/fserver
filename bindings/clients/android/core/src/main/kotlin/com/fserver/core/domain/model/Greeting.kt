package com.fserver.core.domain.model

import com.fserver.core.domain.model.auth.AuthMethod

/**
 * What a device is willing to say before anyone authenticates: which protocol versions it speaks
 * and which of the methods it offers this build recognizes.
 *
 * **Advisory only.** Nothing here is verified — see
 * [com.fserver.core.domain.repository.DevicesRepository.probe]. A method this build does not
 * recognize is left out rather than guessed at.
 */
data class Greeting(
    val protocolVersions: IntRange,
    val methods: List<AuthMethod>,
)
