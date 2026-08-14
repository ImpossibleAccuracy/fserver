package com.fserver.core.domain.repository

/**
 * Represents a lease for a service, allowing it to be started and stopped.
 */
interface ServiceLease {
    /** Indicates whether the service is currently running. Overall state of service. */
    val isRunning: Boolean

    /** Starts the service if it is not already running. */
    suspend fun start()

    /** Stops the service if it is currently running. */
    suspend fun stop()
}
