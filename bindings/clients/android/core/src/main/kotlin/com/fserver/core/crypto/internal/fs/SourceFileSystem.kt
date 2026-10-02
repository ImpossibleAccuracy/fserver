package com.fserver.core.crypto.internal.fs

import com.fserver.core.crypto.model.AtRest
import com.fserver.files.fs.FileSystem

/** A source's files as plaintext, however they sit on disk. Get one from [com.fserver.core.crypto.internal.SourceFileSystems]. */
internal interface SourceFileSystem : FileSystem {
    /** How the file at [locator] sat on disk when the last scan found it; plain when it did not. */
    fun atRestOf(locator: String): AtRest
}
