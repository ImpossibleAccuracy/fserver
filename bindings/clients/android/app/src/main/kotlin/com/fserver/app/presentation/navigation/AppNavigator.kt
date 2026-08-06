package com.fserver.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.fserver.app.presentation.model.Destination

/**
 * Navigation state for the whole app: one back stack per *section*, plus the order in which
 * sections were visited.
 *
 * A section is a destination that owns its own history — the bottom bar tabs, plus whatever
 * [startDestination] the app was launched into (onboarding, for instance). Switching sections
 * keeps the section you left intact, so returning to it lands where you were rather than at its
 * root; that is the "multiple back stacks" behaviour the bottom bar needs.
 *
 * [activeBackStack] is the flattened list handed to `NavDisplay`: every visited section's stack,
 * oldest section first. Keeping the left-behind sections in the list (instead of swapping the
 * displayed list wholesale) is what lets Navigation 3 retain their entry state — an entry loses
 * its saved state the moment its key leaves the back stack.
 *
 * Note: a destination key must not appear in two sections at once. Duplicate keys in the flattened
 * stack collide in Navigation 3's saved-state bookkeeping.
 */
@Stable
class AppNavigator internal constructor(
    val startDestination: Destination,
    private val stacks: Map<Destination, MutableList<NavKey>>,
    private val sectionOrder: MutableList<NavKey>,
) {
    /** Root of the section currently on screen — what the bottom bar highlights. */
    val activeSection: Destination
        get() = sectionOrder.last() as Destination

    /** Flattened back stack across all visited sections, in visit order. */
    val activeBackStack: List<Destination>
        get() = sectionOrder.flatMap { stackOf(it as Destination) }.map { it as Destination }

    /**
     * True when the section root is the screen the user sees, ignoring any [Destination.Overlay]
     * entries stacked on top of it.
     */
    val isSectionRootVisible: Boolean
        get() = stackOf(activeSection).dropLastWhile { it is Destination.Overlay }.size == 1

    /**
     * Switches to [screen] if it is a section root, otherwise pushes it onto the active section.
     *
     * Re-selecting the section already on screen pops it back to its root, matching the usual
     * bottom-bar behaviour.
     */
    fun navigate(screen: Destination) {
        val stack = stacks[screen]
        if (stack == null) {
            stackOf(activeSection).add(screen)
            return
        }

        if (activeSection == screen) {
            popToRoot(stack)
            return
        }

        sectionOrder.remove(screen)
        sectionOrder.add(screen)
    }

    /**
     * Replaces the whole navigation state with [list], discarding every other section's history.
     *
     * Use it for one-way transitions — finishing onboarding, signing out — where the previous
     * stack must not be reachable by back. The first element becomes the active section, so it has
     * to be a section root.
     */
    fun navigateByBackstack(list: List<Destination>) {
        require(list.isNotEmpty()) { "Back stack cannot be empty" }

        val root = list.first()
        val rootStack = requireNotNull(stacks[root]) {
            "$root is not a section root; sections are ${stacks.keys}"
        }

        stacks.values.forEach(::popToRoot)
        rootStack.clear()
        rootStack.addAll(list)

        sectionOrder.clear()
        sectionOrder.add(root)
    }

    /**
     * Pops [count] entries. Pops within the active section first; once at its root, drops the
     * section itself and reveals the previously visited one.
     *
     * @return false if the back stack was already exhausted — the caller should let the system
     * handle back (finish the activity).
     */
    fun navigateUp(count: Int = 1): Boolean {
        repeat(count) {
            if (!pop()) return false
        }
        return true
    }

    private fun pop(): Boolean {
        val stack = stackOf(activeSection)
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }

        if (sectionOrder.size > 1) {
            sectionOrder.removeAt(sectionOrder.lastIndex)
            return true
        }

        return false
    }

    private fun stackOf(section: Destination): MutableList<NavKey> =
        requireNotNull(stacks[section]) { "No back stack for section $section" }

    private fun popToRoot(stack: MutableList<NavKey>) {
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }
}

/**
 * Creates an [AppNavigator] that survives configuration changes and process death.
 *
 * [startDestination] may be any destination — a tab, or a standalone flow such as onboarding,
 * which then gets a section of its own.
 */
@Composable
fun rememberAppNavigator(
    startDestination: Destination,
    topLevelDestinations: List<Destination> = TopLevelDestination.entries.map { it.destination },
): AppNavigator {
    val sections = remember(startDestination, topLevelDestinations) {
        (listOf(startDestination) + topLevelDestinations).distinct()
    }

    // Each section's stack persists itself; `key` keeps the remembered slots tied to the section
    // rather than to loop position.
    val stacks = sections.associateWith { section ->
        key(section) {
            rememberNavBackStack(section)
        }
    }

    val sectionOrder = rememberNavBackStack(startDestination)

    return remember(sections) {
        AppNavigator(
            startDestination = startDestination,
            stacks = stacks,
            sectionOrder = sectionOrder,
        )
    }
}
