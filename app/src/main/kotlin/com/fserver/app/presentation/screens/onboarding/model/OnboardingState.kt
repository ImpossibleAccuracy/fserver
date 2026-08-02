package com.fserver.app.presentation.screens.onboarding.model

data class OnboardingState(
    val pageIndex: Int = 0,
    val pageCount: Int = 3,
) {
    val isLastPage: Boolean get() = pageIndex == pageCount - 1
}