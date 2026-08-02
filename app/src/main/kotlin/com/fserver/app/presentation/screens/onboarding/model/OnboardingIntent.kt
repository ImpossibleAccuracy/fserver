package com.fserver.app.presentation.screens.onboarding.model

sealed interface OnboardingIntent {
    /** The pager settled on a page — user swipe, or the "next" button scrolling it. */
    data class PageSettled(val index: Int) : OnboardingIntent
}
