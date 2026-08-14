package com.fserver.net.security.identity


/** Provider for [LocalIdentity] instances. */
interface IdentityStore {
    val local: LocalIdentity
}
