package com.fserver.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.BuildConfig
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.model.NavigationState
import com.fserver.net.connection.ConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns where the app opens, and nothing else.
 *
 * It used to start every automatic detection method as soon as the available capability set
 * changed. It no longer does: discovery belongs to the screen the user opened for it, which asks
 * for permissions per method and scans only once the user presses the button. A background pass
 * started here would turn a permission the user never saw asked into an empty list.
 */
class AppViewModel(
    private val connectionManager: ConnectionManager<Any>,
) : ViewModel() {

    private val _state = MutableStateFlow(
        NavigationState(
            startDestination = Destination.Onboarding
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.incoming
                .collect { connection ->
                    Timber.i("Incoming connection request from ${connection.peer.advertisedName} via ${connection.peer.endpoint.transport}. Confirmation code: ${connection.peer.confirmationCode}")

                    if (BuildConfig.DEBUG) {
                        // Auto-accept for debugging
                        connection.accept()
                    }
                }
        }
    }
}
