package com.fserver.app.presentation.navigation

import androidx.navigation3.runtime.NavKey
import com.fserver.app.presentation.model.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigator holds plain lists, so the multi-back-stack behaviour is testable without Compose;
 * only the persistence wiring in `rememberAppNavigator` needs a composition.
 */
class AppNavigatorTest {

    private fun navigator(
        startDestination: Destination = Destination.Onboarding,
        topLevel: List<Destination> = listOf(
            Destination.Files,
            Destination.Transfers,
            Destination.Settings,
        ),
    ): AppNavigator {
        val sections = (listOf(startDestination) + topLevel).distinct()
        return AppNavigator(
            startDestination = startDestination,
            stacks = sections.associateWith { mutableListOf<NavKey>(it) },
            sectionOrder = mutableListOf<NavKey>(startDestination),
        )
    }

    @Test
    fun startsAtStartDestination() {
        val navigator = navigator()

        assertEquals(Destination.Onboarding, navigator.currentDestination)
        assertEquals(Destination.Onboarding, navigator.activeSection)
        assertEquals(listOf(Destination.Onboarding), navigator.activeBackStack)
        assertTrue(navigator.isAtSectionRoot)
    }

    @Test
    fun navigateToSectionAppendsItsStack() {
        val navigator = navigator()

        navigator.navigate(Destination.Files)

        assertEquals(Destination.Files, navigator.activeSection)
        assertEquals(
            listOf(Destination.Onboarding, Destination.Files),
            navigator.activeBackStack,
        )
    }

    @Test
    fun navigateToNonSectionPushesOntoActiveSection() {
        val navigator = navigator(startDestination = Destination.Files)

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)

        assertEquals(Destination.Diagnostics, navigator.currentDestination)
        assertEquals(Destination.Settings, navigator.activeSection)
        assertFalse(navigator.isAtSectionRoot)
        assertEquals(
            listOf(Destination.Files, Destination.Settings, Destination.Diagnostics),
            navigator.activeBackStack,
        )
    }

    @Test
    fun switchingSectionsKeepsEachSectionHistory() {
        val navigator = navigator(startDestination = Destination.Files)

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)
        navigator.navigate(Destination.Files)

        // Files is back on top and shows its own root...
        assertEquals(Destination.Files, navigator.currentDestination)
        assertEquals(
            listOf(Destination.Settings, Destination.Diagnostics, Destination.Files),
            navigator.activeBackStack,
        )

        // ...while Settings kept the screen the user had opened there.
        navigator.navigate(Destination.Settings)
        assertEquals(Destination.Diagnostics, navigator.currentDestination)
    }

    @Test
    fun reselectingActiveSectionPopsToItsRoot() {
        val navigator = navigator(startDestination = Destination.Files)

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)
        navigator.navigate(Destination.Settings)

        assertEquals(Destination.Settings, navigator.currentDestination)
        assertTrue(navigator.isAtSectionRoot)
    }

    @Test
    fun navigateUpPopsWithinSectionThenDropsIt() {
        val navigator = navigator(startDestination = Destination.Files)

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)

        assertTrue(navigator.navigateUp())
        assertEquals(Destination.Settings, navigator.currentDestination)

        assertTrue(navigator.navigateUp())
        assertEquals(Destination.Files, navigator.currentDestination)
        assertEquals(listOf(Destination.Files), navigator.activeBackStack)
    }

    @Test
    fun navigateUpAtRootOfLastSectionReportsExhausted() {
        val navigator = navigator(startDestination = Destination.Files)

        assertFalse(navigator.navigateUp())
        assertEquals(listOf(Destination.Files), navigator.activeBackStack)
    }

    @Test
    fun navigateUpWithCountPopsMultiple() {
        val navigator = navigator(startDestination = Destination.Files)

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)

        assertTrue(navigator.navigateUp(count = 2))
        assertEquals(Destination.Files, navigator.currentDestination)
    }

    @Test
    fun navigateByBackstackReplacesEverything() {
        val navigator = navigator()

        navigator.navigate(Destination.Settings)
        navigator.navigate(Destination.Diagnostics)
        navigator.navigateByBackstack(listOf(Destination.Files, Destination.ServerProfile))

        assertEquals(
            listOf(Destination.Files, Destination.ServerProfile),
            navigator.activeBackStack,
        )
        assertEquals(Destination.Files, navigator.activeSection)

        // The discarded sections are back at their roots, not at where they were left.
        navigator.navigate(Destination.Settings)
        assertEquals(Destination.Settings, navigator.currentDestination)
    }

    @Test(expected = IllegalArgumentException::class)
    fun navigateByBackstackRejectsNonSectionRoot() {
        navigator().navigateByBackstack(listOf(Destination.Diagnostics))
    }

    @Test(expected = IllegalArgumentException::class)
    fun navigateByBackstackRejectsEmptyList() {
        navigator().navigateByBackstack(emptyList())
    }
}
