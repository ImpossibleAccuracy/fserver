package com.fserver.app.domain.documents

import androidx.core.net.toUri
import com.fserver.app.BuildConfig
import com.fserver.core.files.SourceLocation

/** This app's own documents' provider. Must match the authority in the manifest. */
const val OwnDocumentsAuthority = "${BuildConfig.APPLICATION_ID}.documents"

/** A folder served by this app itself: a source or destination there would sync the app into itself. */
val SourceLocation.isOwnDocument: Boolean
    get() = this is SourceLocation.Tree && path.toUri().authority == OwnDocumentsAuthority
