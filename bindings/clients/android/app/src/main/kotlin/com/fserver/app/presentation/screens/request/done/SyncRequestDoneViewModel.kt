package com.fserver.app.presentation.screens.request.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.R
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.request.done.model.SyncRequestDoneState
import com.fserver.app.presentation.screens.request.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.request.shared.model.toHostLocationUi
import com.fserver.app.presentation.screens.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SyncRequestDoneViewModel(
    private val key: Destination.SyncRequest.Done,
    private val sourcesRepository: RegisteredSourcesRepository,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    val state: StateFlow<SyncRequestDoneState> = combine(
        sourcesRepository.observeById(key.sourceId),
        trustedDevices.devices,
    ) { source, devices ->
        source ?: return@combine SyncRequestDoneState()

        val location = source.location.toHostLocationUi()

        SyncRequestDoneState(
            label = source.label,
            deviceName = devices
                .filter { it.deviceId == source.deviceId }
                .maxByOrNull { it.lastSeen }
                ?.displayName
                ?: source.deviceId,
            modeRes = source.syncMode.toUi().titleRes,
            locationLabel = (location as? HostLocationUi.Folder)?.label,
            locationRes = R.string.sync_request_location_internal_title
                .takeIf { location is HostLocationUi.AppStorage },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncRequestDoneState(),
    )
}
