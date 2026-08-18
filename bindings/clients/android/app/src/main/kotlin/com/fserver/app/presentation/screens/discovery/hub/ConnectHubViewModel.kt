package com.fserver.app.presentation.screens.discovery.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.composable.model.firstAction
import com.fserver.app.presentation.screens.discovery.shared.toCardUi
import com.fserver.app.presentation.screens.discovery.hub.model.ConnectHubState
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The connection fork holds one fact — what the phone is connected to — and starts nothing.
 * Every search on this screen is a link to somewhere the user has to go and press a button.
 */
class ConnectHubViewModel(
    private val networkInfoRepository: NetworkInfoRepository,
    private val requirementsChecker: RequirementsChecker,
    private val authManager: AuthManager,
) : ViewModel() {
    /** What Android still wants before it will name the network. */
    private val networkReport = MutableStateFlow(RequirementReport.Satisfied)

    val state: StateFlow<ConnectHubState> = combine(
        networkInfoRepository.networkInfo,
        networkReport,
    ) { network, report ->
        ConnectHubState(
            network = network.toCardUi(),
            networkAction = report.firstAction,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectHubState(),
        )

    init {
        viewModelScope.launch {
            networkInfoRepository.networkInfo.collect { recheckNetwork() }
        }
    }

    /**
     * The report is a snapshot and the user can change any of it from outside the app, so it is
     * re-read whenever the screen comes back - and after the user has acted on
     * [ConnectHubState.networkAction].
     *
     * The re-read of the network itself is the other half: granting location changes nothing the
     * platform reports on its own, so the name stays redacted until it is asked for again.
     */
    fun onNetworkResolved() {
        networkInfoRepository.refresh()
        recheckNetwork()
    }

    private fun recheckNetwork() {
        viewModelScope.launch {
            networkReport.value = requirementsChecker.forNetworkInfo()
        }
    }

    fun onSkip() {
        viewModelScope.launch {
            authManager.ensureLoggedIn()
        }
    }
}
