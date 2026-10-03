package com.fserver.core.sync.server.handler

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.clock.ClockSkews
import com.fserver.core.util.TimeProvider
import com.fserver.net.session.PeerSession
import timber.log.Timber

/**
 * Answers `ClockProbe` with this device's clock. Records the asker's offset too, one-way: off by the
 * link latency, which is far below the drift that matters.
 */
internal class ClockProbeHandler(
    private val timeProvider: TimeProvider,
    private val skews: ClockSkews,
) {
    suspend fun handle(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.ClockProbe.Request,
        session: PeerSession<FileServerMessages>,
    ) {
        val receivedAt = nowMs()
        skews.record(session.identity.deviceId, message.sentAt - receivedAt)

        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot answer ClockProbe from ${session.identity.deviceId}: no reply channel")
            return
        }

        reply(FileServerMessages.ClockProbe.Reading(receivedAt = receivedAt, repliedAt = nowMs()))
    }

    private fun nowMs(): Long = timeProvider.now().toEpochMilliseconds()
}
