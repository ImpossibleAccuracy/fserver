package com.fserver.core.requirement

/**
 * What is still missing before an operation could succeed. Only *unmet* requirements are listed,
 * so an empty report means ready to run.
 *
 * @param [solvable] - the app can start the fix right now:
 * launch a permission request, open a settings screen,
 * show the Play services resolution dialog.
 * Worth putting a button/dialog in front of the user.
 * @param [blockers] - everything else:
 * absent hardware, a permission the host's manifest does not declare,
 * a Play services error the user cannot resolve,
 * transport that does not carry the needed capability.
 * Worth an explanation, not a button.
 *
 * A report is a snapshot. Anything in [solvable] invalidates it the moment the user acts on it,
 * so re-check rather than caching. Resolving one entry can also reveal another that was hidden
 * behind it - Bluetooth's on/off state cannot be read until `BLUETOOTH_CONNECT` is granted, so a
 * missing permission is reported first and the toggle only afterward.
 */
data class RequirementReport(
    val blockers: List<Requirement>,
    val solvable: List<Requirement>,
) {
    /** Nothing in the way. */
    val isSatisfied: Boolean get() = blockers.isEmpty() && solvable.isEmpty()

    /** Something is in the way, and the user acting on it is enough to clear it. */
    val isResolvableByUser: Boolean get() = blockers.isEmpty() && solvable.isNotEmpty()

    companion object {
        val Satisfied = RequirementReport(blockers = emptyList(), solvable = emptyList())
    }
}
