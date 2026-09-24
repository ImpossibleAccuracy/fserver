package com.fserver.app.presentation.screens.source.setup.pick

import android.content.Context
import com.fserver.app.presentation.screens.source.setup.access.DevSourceBucket
import com.fserver.app.presentation.screens.source.setup.pick.model.SourcePickIntent
import com.fserver.app.presentation.screens.source.setup.pick.model.SourcePickState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.StorageVolumes
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourcePickHandler(
    private val context: Context,
    private val requirementsChecker: RequirementsChecker,
    private val reporter: ErrorReporter,
    scope: CoroutineScope,
) {
    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<SourcePickState> = editable
        .map {
            SourcePickState(
                moreExpanded = it.moreExpanded,
                unavailable = it.blocked.keys,
            )
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SourcePickState())

    init {
        scope.launch { checkBlockers() }
    }

    fun onIntent(intent: SourcePickIntent) {
        when (intent) {
            SourcePickIntent.MoreToggled ->
                editable.update { it.copy(moreExpanded = !it.moreExpanded) }

            is SourcePickIntent.UnavailablePicked ->
                editable.value.blocked[intent.kind]?.let(reporter::report)
        }
    }

    private suspend fun checkBlockers() {
        val blocked = SourceKindUi.entries
            .associateWith { kind ->
                kind.location()?.let { requirementsChecker.forSource(it) }
            }
            .filterValues { it != null && it.blockers.isNotEmpty() }
            .mapValues { it.value!! }

        editable.update { it.copy(blocked = blocked) }
    }

    private fun SourceKindUi.location(): SourceLocation? = when (this) {
        SourceKindUi.Media -> SourceLocation.Media
        SourceKindUi.WholeDevice -> StorageVolumes.fromContext(context)
        SourceKindUi.AppStorage -> SourceLocation.Internal(bucket = DevSourceBucket)
        SourceKindUi.Folder -> null
    }

    private data class Editable(
        val moreExpanded: Boolean = false,
        val blocked: Map<SourceKindUi, RequirementReport> = emptyMap(),
    )
}
