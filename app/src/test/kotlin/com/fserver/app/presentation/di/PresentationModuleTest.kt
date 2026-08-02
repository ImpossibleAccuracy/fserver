package com.fserver.app.presentation.di

import com.fserver.app.di.presentationModule
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class PresentationModuleTest {

    /**
     * Every ViewModel is resolved through Koin at navigation time, so a missing binding
     * would only surface as a crash on the screen that needs it. This checks the whole
     * graph up front.
     *
     * `String` is declared as an extra type because `PairingViewModel` takes the tapped
     * device id from `parametersOf`, not from the graph.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every screen's dependencies can be resolved`() {
        presentationModule.verify(extraTypes = listOf(String::class))
    }
}
