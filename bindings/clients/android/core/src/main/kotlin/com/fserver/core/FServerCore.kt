package com.fserver.core

import com.fserver.core.di.coreModule
import com.fserver.core.files.FilesController
import com.fserver.core.network.NetworkController
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.remote.PeerRequestServer
import com.fserver.files.FilesNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Entry point to `:core`. Build one per process, keep it, [close] it when the host dies.
 *
 * This is the module's whole runtime surface: every consumer - this app, another Android UI,
 * instrumentation test - reaches the engine through here and never touches an implementation class.
 * The only other thing `:core` publishes is the storage SPI under `store/`, which a backend
 * implements and a UI never calls - see [com.fserver.core.store.FServerStorageApi].
 *
 * `:core` wires itself with Koin, but in a **private** [Koin] instance created by
 * [koinApplication], not the global `startKoin` context.
 *
 * Consequences worth knowing:
 * - The host's DI framework is its own business. Koin, Hilt, or hand-wiring all work, because
 *   nothing here asks the host for a container. The host registers this object as one singleton.
 * - Two `Koin` instances coexist fine. A host on Koin gets no definition clash with `:core`,
 *   since the graphs never merge.
 * - Koin stays an `implementation` dependency, off the consumer's compile classpath.
 *
 * Alternatives considered, if this ever needs revisiting:
 * - **Hand-rolled composition root** - same facade, `by lazy` chain instead of a container.
 *   Drops the Koin dependency entirely at the cost of writing (and reordering) the wiring by hand.
 * - **Koin module as published API** (what this replaced) - host installs `coreModule` into its
 *   own graph. Ergonomic for Koin hosts, but forces Koin, its version, and a global context on
 *   everyone else.
 * - **Separate adapter artifacts** (`core-koin`, `core-hilt`) over a DI-free `:core`. The
 *   library-standard shape; worth it only once a second consumer actually asks for it.
 * - **Public implementation constructors** - maximum host control, but every internal refactor
 *   becomes a breaking change. Rejected.
 */
class FServerCore private constructor(
    private val koin: Koin,
    private val network: NetworkController,
    /** Non-null only when the core created the scope, and so is the one allowed to cancel it. */
    private val ownedScope: CoroutineScope?,
) : AutoCloseable {

    /** Discovery, connection attempts, and the list of devices currently reachable. */
    val deviceDetection: DevicesRepository by lazy { koin.get() }

    /** The network this device is on, as far as detection is concerned. */
    val networkInfo: NetworkInfoRepository by lazy { koin.get() }

    /**
     * What the OS still demands - permissions, radios, hardware - before an operation can run.
     * The host owns the fix: only it can launch a permission request or a settings screen.
     */
    val requirements: RequirementsChecker by lazy { koin.get() }

    /** Walking a directory the user picked, before it is registered as a source. */
    val files: FilesController by lazy { koin.get() }

    /**
     * The registry of synced sources: register, re-configure, drop, and kick off a pass.
     * Listing what is registered is a UI concern - inject `RegisteredSourcesRepository` for that.
     */
    val sources: SourcesController by lazy { koin.get() }

    /**
     * Starts answering what peers ask of this device - index requests, transfers, deletes.
     *
     * @return `null` if already serving, a `Job` that completes when the listener is canceled otherwise.
     */
    fun startServing() =
        koin.get<PeerRequestServer>().start()

    /**
     * Stops answering peers and drops every request still in flight. [startServing] works again
     * afterwards - unlike [shutdown], this leaves the instance usable.
     */
    suspend fun stopServing() =
        koin.get<PeerRequestServer>().stop()

    /**
     * Tears down the internal graph and stops background work. After this the instance is dead -
     * build a new one rather than reusing it.
     */
    suspend fun shutdown() {
        stopServing()
        network.shutdown()
        koin.close()
        ownedScope?.cancel()
    }

    /**
     * [shutdown] for callers with no coroutine to hand. Blocks the calling thread while `:net`
     * flushes its CLOSE frames - up to a few seconds - so never call it on the main thread.
     */
    override fun close() {
        runBlocking { shutdown() }
    }

    companion object {
        fun create(config: FServerConfig): FServerCore {
            val scope = config.backgroundScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val authenticator = InteractivePeerAuthenticator()

            val network = NetworkController(
                config = config,
                authenticator = authenticator,
                coroutineScope = scope,
            )

            val files = FilesNode.create(config.context)

            val koin = koinApplication {
                modules(
                    coreModule(config.context, scope),
                    module {
                        single { files }
                        single { config.storage }
                        single { network }
                        single { authenticator }
                    }
                )
            }.koin

            return FServerCore(
                koin = koin,
                network = network,
                ownedScope = scope.takeIf { config.backgroundScope == null },
            )
        }
    }
}
