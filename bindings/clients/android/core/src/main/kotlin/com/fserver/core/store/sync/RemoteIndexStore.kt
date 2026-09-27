package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.index.RemoteIndexedFile
import kotlinx.coroutines.flow.Flow

/**
 * The last index each source's peer reported, as this device heard it. [FileIndexStore] is the same
 * bookkeeping for this side.
 *
 * A cache, not a source of truth: a pass fetches the peer's index over the wire and writes the
 * answer through here, and the peer also pushes its own at the end of its pass. Nothing plans from
 * these rows today, so a stale or missing set costs a pass nothing.
 *
 * Every record is a claim the peer made about itself - never a claim about bytes on this device.
 * See [com.fserver.core.store.FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface RemoteIndexStore {
    val all: Flow<List<RemoteIndexedFile>>

    /** What [sourceId]'s peer last reported, or empty when nothing has been heard yet. */
    suspend fun files(sourceId: String): List<RemoteIndexedFile>

    /** Replaces everything recorded for [sourceId] with [files], attributed to [deviceId]. */
    suspend fun replace(sourceId: String, deviceId: String, files: Collection<RemoteIndexedFile>)

    /** Records [file] under its source, replacing the earlier row for it. Attributed to [deviceId]. */
    suspend fun upsert(deviceId: String, file: RemoteIndexedFile)

    /** Forgets what [sourceId]'s peer reported. Touches no bytes on either device. */
    suspend fun clear(sourceId: String)
}
