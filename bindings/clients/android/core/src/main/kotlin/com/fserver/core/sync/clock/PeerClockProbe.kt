package com.fserver.core.sync.clock

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.util.TimeProvider

/** Measures the peer's clock offset NTP-style, so a pass knows whether LWW can trust it. */
internal class PeerClockProbe(
    private val connector: PeerConnector,
    private val timeProvider: TimeProvider,
    private val skews: ClockSkews,
) {
    suspend fun measure(source: SourceEntry) {
        val device = connector.connectToDevice(source)

        val sentAt = nowMs()
        val response = device.request(FileServerMessages.ClockProbe.Request(sentAt)).getOrThrow()
        val receivedAt = nowMs()

        check(response is FileServerMessages.ClockProbe.Reading) {
            "Unexpected response to ClockProbe from ${source.deviceId}: $response"
        }

        val offset = ((response.receivedAt - sentAt) + (response.repliedAt - receivedAt)) / 2
        skews.record(source.deviceId, offset)
    }

    private fun nowMs(): Long = timeProvider.now().toEpochMilliseconds()
}
