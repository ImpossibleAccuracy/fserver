package com.fserver.net.config

import com.fserver.net.security.auth.AuthMethod

internal fun interface AuthMethodFactory {
    fun create(environment: NodeConfigEnvironment): AuthMethod
}
