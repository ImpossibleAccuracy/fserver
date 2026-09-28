package com.fserver.core.sync.server

import com.fserver.core.sync.server.handler.upload.SessionUploads
import kotlinx.coroutines.CoroutineScope

/**
 * Per-session state of the exchange with one peer. Owned by the coroutine serving that session,
 * so a reconnect starts clean.
 *
 * [scope] is that coroutine's, so every piece of work started here dies with the session feeding it.
 * Each request family keeps what it needs across messages in a part of its own.
 */
internal class SessionContext(
    val scope: CoroutineScope,
    val uploads: SessionUploads,
)
