package com.fserver.app.presentation.shared.browser.model

import kotlinx.serialization.Serializable

/** A file of the index. [fileId] names a file only within its source, so it never travels alone. */
@Serializable
data class FileKey(val fileId: String, val sourceId: String)
