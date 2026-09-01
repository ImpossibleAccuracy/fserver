package com.fserver.core.storage

import com.fserver.core.sync.SyncPreferences
import kotlinx.coroutines.flow.Flow

/**
 * The sync settings, as a screen needs them: observable.
 *
 * Read-only on purpose. Writing them is an engine action - call `SourcesController
 * .updatePreferences`, which persists the change and re-runs a pass under the new settings.
 */
interface SyncPreferencesRepository {
    val preferences: Flow<SyncPreferences>
}
