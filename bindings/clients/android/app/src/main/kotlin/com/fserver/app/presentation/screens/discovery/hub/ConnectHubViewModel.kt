package com.fserver.app.presentation.screens.discovery.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.model.toCardUi
import com.fserver.app.presentation.screens.discovery.hub.model.ConnectHubState
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The connection fork holds one fact — what the phone is connected to — and starts nothing.
 * Every search on this screen is a link to somewhere the user has to go and press a button.
 */
class ConnectHubViewModel(
    networkInfoRepository: NetworkInfoRepository,
    private val requirementsChecker: RequirementsChecker,
    private val authManager: AuthManager,
) : ViewModel() {

    val state: StateFlow<ConnectHubState> = networkInfoRepository.networkInfo
        .map { network ->
            ConnectHubState(
                network = network.toCardUi(named = requirementsChecker.forNetworkInfo().isSatisfied)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectHubState(),
        )

    fun onSkip() {
        viewModelScope.launch {
            authManager.ensureLoggedIn()
        }
    }
}
