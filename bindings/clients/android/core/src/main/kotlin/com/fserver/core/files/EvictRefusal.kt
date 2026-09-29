package com.fserver.core.files

import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile

/** Why a file's local bytes may not be evicted: freeing them would leave no confirmed copy. */
enum class EvictRefusal {
    /** No bytes here to free. */
    NotHere,
    Pinned,

    /** Either side's bytes are not hashed yet, so the copies cannot be compared. */
    Unverified,
    NotOnPeer,
    PeerDiffers,

    /** Same bytes under histories not merged yet: evicting now would conflict with the next edit. A pass merges them. */
    Unmerged,
}

/** Null when the peer confirmably holds these exact bytes under the same version. */
internal fun LocalIndexedFile.evictRefusal(remote: RemoteIndexedFile?): EvictRefusal? {
    val present = state as? LocalIndexedFile.State.Present ?: return EvictRefusal.NotHere

    return when {
        present.pinned -> EvictRefusal.Pinned
        hash == null || hashStale -> EvictRefusal.Unverified
        remote == null || remote.state !is LocalIndexedFile.State.Present -> EvictRefusal.NotOnPeer
        remote.hash == null -> EvictRefusal.Unverified
        remote.hash != hash -> EvictRefusal.PeerDiffers
        remote.version?.vector != version?.vector -> EvictRefusal.Unmerged
        else -> null
    }
}
