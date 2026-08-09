package com.fserver.core

import android.content.Context
import com.fserver.core.di.coreModule
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.NetworkInfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.Koin
import org.koin.dsl.koinApplication

/**
 * Everything [FServerCore] needs from its host.
 *
 * @param context application context.
 * @param backgroundScope scope for work that must survive the caller (advertising, discovery).
 * `null` means the core creates and owns one, and cancels it on [FServerCore.close].
 */
data class FServerConfig(
    val context: Context,
    val backgroundScope: CoroutineScope? = null,
)

/**
 * Entry point to `:core`. Build one per process, keep it, [close] it when the host dies.
 *
 * This is the module's whole public surface: every consumer - this app, another Android UI,
 * instrumentation test - reaches the engine through here and never touches an implementation class.
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
    /** Non-null only when the core created the scope, and so is the one allowed to cancel it. */
    private val ownedScope: CoroutineScope?,
) : AutoCloseable {

    /** Discovery, connection attempts, and the list of devices currently reachable. */
    val deviceDetection: DeviceDetectionRepository by lazy { koin.get() }

    /** The network this device is on, as far as detection is concerned. */
    val networkInfo: NetworkInfoRepository by lazy { koin.get() }

    /**
     * Tears down the internal graph and stops background work. After this the instance is dead -
     * build a new one rather than reusing it.
     */
    override fun close() {
        koin.close()
        ownedScope?.cancel()
    }

    companion object {
        fun create(config: FServerConfig): FServerCore {
            val scope = config.backgroundScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val koin = koinApplication {
                modules(coreModule(config.context, scope))
            }.koin

            return FServerCore(
                koin = koin,
                ownedScope = scope.takeIf { config.backgroundScope == null },
            )
        }
    }
}
