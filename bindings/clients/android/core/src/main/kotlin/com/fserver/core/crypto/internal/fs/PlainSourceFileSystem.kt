package com.fserver.core.crypto.internal.fs

import com.fserver.core.crypto.model.AtRest
import com.fserver.files.fs.FileSystem

/** A source no file of which is ever sealed. */
internal class PlainSourceFileSystem(inner: FileSystem) : SourceFileSystem, FileSystem by inner {
    override fun atRestOf(locator: String): AtRest = AtRest.Plain
}
