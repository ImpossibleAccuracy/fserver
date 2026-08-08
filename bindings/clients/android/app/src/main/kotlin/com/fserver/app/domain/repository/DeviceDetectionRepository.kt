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
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     * Starting a method that is already running is a no-op.
     */
    suspend fun startDetection(request: DeviceDetectionRequest)
}
