package com.fserver.core.data.repository

import com.fserver.core.domain.repository.ServiceLease
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Controller that manages the lifecycle of a service, allowing it to be started and stopped
 * based on the number of active consumers.
 *
 * @property startService Function to start the service.
 * @property stopService Function to stop the service.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class ServiceLifecycleController(
    private val startService: suspend () -> Unit,
    private val stopService: suspend () -> Unit
) {
    private val mutex = Mutex()
    private val consumers = AtomicInt(0)

    private suspend fun tryStartService() = mutex.withLock {
        if (consumers.incrementAndFetch() > 0) {
            startService()
        }
    }

    private suspend fun tryStopService() = mutex.withLock {
        if (consumers.decrementAndFetch() == 0) {
            stopService()
        }
    }

    /**
     * Creates a new lease for the service, allowing it to be started and stopped.
     *
     * @return A new instance of [ServiceLease].
     */
    fun newLease(): ServiceLease = ServiceLeaseImpl()

    private inner class ServiceLeaseImpl : ServiceLease {
        private val state = AtomicBoolean(false)

        override val isRunning: Boolean
            get() = consumers.load() > 0

        override suspend fun start() {
            if (state.compareAndSet(false, true)) {
                tryStartService()
            }
        }

        override suspend fun stop() {
            if (state.compareAndSet(true, false)) {
                tryStopService()
            }
        }
    }
}
