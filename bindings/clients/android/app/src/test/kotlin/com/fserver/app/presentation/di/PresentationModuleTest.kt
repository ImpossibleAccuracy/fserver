package com.fserver.app.presentation.di

import android.content.Context
import com.fserver.app.di.presentationModule
import com.fserver.core.di.coreModule
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class PresentationModuleTest {

    /**
     * Every ViewModel is resolved through Koin at navigation time, so a missing binding
     * would only surface as a crash on the screen that needs it. This checks the whole
     * graph up front.
     *
     * Verified against `coreModule` too, because ViewModels are injected with
     * repositories - checking `presentationModule` on its own cannot resolve a single one of them.
     *
     * Extra types are the ones Koin never sees a definition for: `Context` is supplied by
     * `androidContext()` at start-up, and `String` is the device id `PairingViewModel` takes from
     * `parametersOf`.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every screen's dependencies can be resolved`() {
        module {
            includes(presentationModule, coreModule)
        }.verify(
            extraTypes = listOf(String::class, Context::class),
        )
    }
}
