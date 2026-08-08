package com.fserver.app.domain.repository

import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow

interface DeviceDetectionRepository {
    val onlineDevices: Flow<List<FoundDevice>>

    /**
     * Methods scanning right now. Per-method rather than a single flag: several run at
     * once, they finish at wildly different times, and the UI has to say which one the
     * user is still waiting on.
     */
    val runningScanningMethods: Flow<Set<DetectionMethod>>

    /**
     * The device with [id], or null once it is no longer among [onlineDevices].
     */
    fun device(id: String): Flow<FoundDevice?>

    /**
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     *
     * Returns what *this* run found. The address paths — typed or scanned — need the one
     * device they asked about, and cannot pick it out of the accumulated list without
     * guessing which entry is theirs. Starting a request that is already running is a no-op
     * and returns nothing.
     */
    suspend fun startDetection(request: DeviceDetectionRequest): List<FoundDevice>
}
