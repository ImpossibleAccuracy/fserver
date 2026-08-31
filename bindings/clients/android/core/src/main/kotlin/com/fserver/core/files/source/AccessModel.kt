package com.fserver.core.files.source

/**
 * What the engine is allowed to do with a source's files.
 *
 * TODO: placeholder values. The real set is the spec's mode vocabulary (sync / auto-upload /
 *  offload / host) - see `docs/`. Nothing reads this yet beyond persisting it.
 */
enum class AccessModel {
    /** Read the files, never write or remove them. */
    ReadOnly,

    /** Read and write, originals stay put. */
    ReadWrite,

    /**
     * Read, then free local space once a copy is confirmed elsewhere. Eviction is not deletion -
     * it must never propagate as a user delete to the backup device.
     */
    Evicting,
}
