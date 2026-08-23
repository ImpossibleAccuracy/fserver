package com.fserver.app.presentation.screens.onboarding.model

data class OnboardingState(
    val pageIndex: Int = 0,
    val pageCount: Int = 3,
) {
    /**
     * The last page is the fork, not a summary: it offers the two ways into the app instead of
     * a "get started" button, so the primary action disappears there.
     */
    val isFork: Boolean get() = pageIndex == pageCount - 1
}
