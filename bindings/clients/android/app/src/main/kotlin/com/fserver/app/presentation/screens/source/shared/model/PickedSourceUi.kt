package com.fserver.app.presentation.screens.source.shared.model

import androidx.compose.runtime.Immutable

/** What the access step's scan found, for the screens after it. */
@Immutable
data class PickedSourceUi(
    val files: Int,
    val bytes: Long,
    val uri: String? = null,
    val label: String = "",
)
