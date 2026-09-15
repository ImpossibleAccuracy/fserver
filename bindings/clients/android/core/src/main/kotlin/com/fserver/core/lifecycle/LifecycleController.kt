package com.fserver.core.lifecycle

import com.fserver.core.lifecycle.network.AutoAcceptCoordinator
import com.fserver.core.lifecycle.network.PresenceController
import com.fserver.core.lifecycle.sync.AutoSyncCoordinator

/** Controls the lifecycle of the host. */
class LifecycleController internal constructor(
    private val autoSyncCoordinator: AutoSyncCoordinator,
    private val autoAcceptCoordinator: AutoAcceptCoordinator,
    private val presenceController: PresenceController,
) {
    /**
     * Starts watching whoever discovery finds: a device showing up with an active source
     * registered against it gets a pass over those sources, and nothing else is disturbed.
     *
     * Starts no scan - the host does that through `deviceDetection.discovery` - so this only
     * reacts while something is scanning or a peer dials in.
     *
     * @return `null` if already watching, a `Job` that completes when the watcher is canceled otherwise.
     */
    fun startAutoSync() = autoSyncCoordinator.start()

    /** Stops the watcher. [startAutoSync] works again afterward. */
    suspend fun stopAutoSync() = autoSyncCoordinator.stop()

    /**
     * Answers a device that dials in without asking the user, as long as any active source names it.
     * Anyone else is still handed to the host through `deviceDetection.incoming`.
     */
    fun startAutoAccept() = autoAcceptCoordinator.start()

    /** Back to asking about every incoming request. [startAutoAccept] works again afterward. */
    fun stopAutoAccept() = autoAcceptCoordinator.stop()

    /** The presence controller, which the host starts and stops as needed. */
    val presence = presenceController

    /** Hands over the presence controller to the host, which will start and stop it as needed. */
    fun presenceHandover() = presenceController.handover()

    suspend fun stopAll() {
        presenceController.stop()
        autoAcceptCoordinator.stop()
        autoSyncCoordinator.stop()
    }
}
