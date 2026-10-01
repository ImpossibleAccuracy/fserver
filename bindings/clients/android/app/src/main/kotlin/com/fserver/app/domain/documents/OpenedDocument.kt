package com.fserver.app.domain.documents

import android.os.ParcelFileDescriptor
import com.fserver.core.files.access.SourceFileReader

sealed interface OpenedDocument {
    /** The file itself: hand it over as is. */
    data class Descriptor(val descriptor: ParcelFileDescriptor) : OpenedDocument

    /** Bytes that only `:core` can make sense of, such as an encrypted source's. */
    data class Reader(val reader: SourceFileReader) : OpenedDocument
}