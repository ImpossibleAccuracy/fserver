package com.fserver.net.peer

import com.fserver.net.security.auth.AuthMethodId

/**
 * Everything a device will tell a stranger: which protocol versions it speaks and which ways it is
 * willing to be authenticated. Enough to put the right prompt in front of the user, and nothing
 * that says which device answered.
 *
 * **Advisory only.** Nothing here is verified, and nothing derived from it may decide anything: a
 * greeting is allowed to lie. The connection that follows says hello again on its own link and
 * binds that hello to the authentication, so a lie told here fails there.
 */
data class PublicGreeting(
    val protocolVersions: IntRange,
    val methods: List<AuthMethodId>,
)
