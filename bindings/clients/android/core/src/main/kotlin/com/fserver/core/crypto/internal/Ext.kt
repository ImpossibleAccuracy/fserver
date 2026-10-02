package com.fserver.core.crypto.internal

import com.fserver.core.crypto.model.AtRest
import com.fserver.files.fs.FsFile

/** How the bytes of a file a [com.fserver.core.crypto.internal.fs.SourceFileSystem] handed out sit on disk. */
internal val FsFile.atRest: AtRest
    get() = (this as? SealedFsFile)?.atRest ?: AtRest.Plain
