package com.fserver.app.presentation.di

import android.content.Context
import com.fserver.app.di.coreModule
import com.fserver.app.di.dataModule
import com.fserver.app.di.domainModule
import com.fserver.app.di.presentationModule
import com.fserver.app.presentation.model.Destination
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
     * That is the app's own bridge module: `:core` wires itself in a private container, so its
     * internals are not part of this graph and nothing here can check them.
     *
     * Extra types are the ones Koin never sees a definition for: `Context` is supplied by
     * `androidContext()` at start-up, and the `Destination` keys are the nav keys their
     * ViewModels take from `parametersOf`.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every screen's dependencies can be resolved`() {
        module {
            includes(dataModule, domainModule, presentationModule, coreModule)
        }.verify(
            extraTypes = listOf(
                String::class,
                Context::class,
                Destination.Pairing::class,
                Destination.Files::class,
                Destination.Files.SourceDetails::class,
                Destination.Settings.DeviceDetails::class,
                Destination.Settings.PinChange::class,
                Destination.Settings.StorageSource::class,
                Destination.Source.Done::class,
                Destination.Source.Progress::class,
                Destination.Source.Request.Details::class,
                Destination.Source.Request.Location::class,
                Destination.Source.Request.Preferences::class,
            ),
        )
    }
}
