package com.fserver.app.data.oneshot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.app.domain.oneshot.OneShotDestinations
import com.fserver.app.domain.oneshot.OneShotRepository
import com.fserver.app.domain.oneshot.isAwaitingAnswer
import com.fserver.core.files.SourceLocation
import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.storage.OneShotTransfersRepository
import com.fserver.core.sync.progress.SyncProgressRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class OneShotRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val requirements: RequirementsChecker,
    private val transfers: OneShotTransfersRepository,
    private val controller: OneShotTransfersController,
    private val progress: SyncProgressRepository,
    private val notifier: OneShotNotifier,
) : OneShotRepository {

    override val autoAccept: Flow<Boolean> = dataStore.data.map { it[AUTO_ACCEPT] ?: false }

    override val destination: Flow<SourceLocation.Hostable> = dataStore.data.map { prefs ->
        prefs[DESTINATION]?.let(::decode) ?: default()
    }

    override suspend fun setAutoAccept(enabled: Boolean) {
        dataStore.edit { it[AUTO_ACCEPT] = enabled }
    }

    override suspend fun setDestination(location: SourceLocation.Hostable) {
        dataStore.edit { it[DESTINATION] = encode(location) }
    }

    override suspend fun runBackgroundWork() {
        val attempted = mutableSetOf<String>()

        combine(transfers.transfers, autoAccept, ::Pair).collect { (all, enabled) ->
            if (!enabled) return@collect

            all.filter { it.isAwaitingAnswer && attempted.add(it.id) }.forEach { accept(it) }
        }
    }

    override suspend fun linkVisibility(appVisible: Flow<Boolean>) = coroutineScope {
        launch {
            progress.oneShotTransfers
                .map { live -> live.any { !it.isFinished } }
                .distinctUntilChanged()
                .filter { it }
                .collect { notifier.startTransferService() }
        }

        val shown = mutableSetOf<String>()
        combine(transfers.transfers, appVisible, autoAccept) { all, visible, autoAccept ->
            if (visible || autoAccept) emptyList() else all.filter { it.isAwaitingAnswer }
        }.collect { offers ->
            val ids = offers.mapTo(mutableSetOf()) { it.id }

            (shown - ids).forEach(notifier::dismissOffer)
            shown.retainAll(ids)

            offers.filter { shown.add(it.id) }.forEach(notifier::showOffer)
        }
    }

    private suspend fun accept(transfer: OneShotTransfer) {
        controller.accept(transfer.id, destination.first())
            .onSuccess { Timber.i("Auto-accepted transfer ${transfer.id} from ${transfer.peer.displayName}") }
            .onFailure { Timber.w(it, "Could not auto-accept transfer ${transfer.id}") }
    }

    // Downloads needs no grant from API 29, and WRITE_EXTERNAL_STORAGE below it.
    private suspend fun default(): SourceLocation.Hostable =
        if (requirements.forSource(OneShotDestinations.Downloads).isSatisfied) OneShotDestinations.Downloads
        else OneShotDestinations.AppStorage

    private companion object {
        val AUTO_ACCEPT = booleanPreferencesKey("one_shot_auto_accept")
        val DESTINATION = stringPreferencesKey("one_shot_destination")

        fun encode(location: SourceLocation.Hostable): String = when (location) {
            is SourceLocation.Downloads -> "downloads:${location.directory}"
            is SourceLocation.Internal -> "internal:${location.bucket}"
            is SourceLocation.Tree -> "tree:${location.path}"
            is SourceLocation.Directory -> "directory:${location.path}"
        }

        /** Null for a value this version cannot read: the default is used instead. */
        fun decode(value: String): SourceLocation.Hostable? {
            val payload = value.substringAfter(':')
            return when (value.substringBefore(':')) {
                "downloads" -> SourceLocation.Downloads(payload)
                "internal" -> SourceLocation.Internal(payload)
                "tree" -> SourceLocation.Tree(payload)
                "directory" -> SourceLocation.Directory(payload)
                else -> null
            }
        }
    }
}
