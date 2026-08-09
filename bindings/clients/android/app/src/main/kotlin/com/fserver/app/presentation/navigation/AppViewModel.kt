package com.fserver.app.presentation.navigation

import androidx.lifecycle.ViewModel
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.model.NavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns where the app opens, and nothing else.
 *
 * It used to start every automatic detection method as soon as the available capability set
 * changed. It no longer does: discovery belongs to the screen the user opened for it, which asks
 * for permissions per method and scans only once the user presses the button. A background pass
 * started here would turn a permission the user never saw asked into an empty list.
 */
class AppViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        NavigationState(
            startDestination = Destination.Onboarding
        )
    )
    val state = _state.asStateFlow()
}
