package com.fserver.app.presentation.screens.onboarding.model

data class OnboardingState(
    val pageIndex: Int = 0,
    val pageCount: Int = 3,
) {
    /** The last page ends the tour: its primary action is the way in, not another "next". */
    val isLast: Boolean get() = pageIndex == pageCount - 1
}
