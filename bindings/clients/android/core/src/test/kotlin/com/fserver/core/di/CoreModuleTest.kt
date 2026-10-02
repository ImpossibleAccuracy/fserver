package com.fserver.core.di

import android.content.ContextWrapper
import com.fserver.core.FServerConfig
import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.crypto.StorageKeysStore
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import java.lang.reflect.Proxy
import java.security.SecureRandom
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The engine graph is wired by hand, so a class that gains a constructor parameter nobody declares
 * fails at `FServerCore.create` - on the host's device, not here. This catches it at build time,
 * across every area module [coreModule] pulls in.
 */
class CoreModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition in the engine graph resolves`() {
        val module = coreModule(
            config = FServerConfig(
                context = ContextWrapper(null),
                backgroundScope = CoroutineScope(EmptyCoroutineContext),
            ),
            storage = storageStub(),
        )

        // Function0: lambdas handed in by a `single { }` block, which verify cannot see through.
        // SealedFiles is built by hand from the host's ciphers and key store.
        module.verify(
            extraTypes = listOf(Function0::class),
            injections = injectedParameters(
                definition<SealedFiles>(List::class, StorageKeysStore::class, SecureRandom::class),
            ),
        )
    }

    /** A proxy rather than a fake: the store SPI is closed to subclassing outside a backend. */
    private fun storageStub(): FServerStorage = Proxy.newProxyInstance(
        FServerStorage::class.java.classLoader,
        arrayOf(FServerStorage::class.java),
    ) { _, _, _ -> null } as FServerStorage
}
