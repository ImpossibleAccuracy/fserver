package com.fserver.app.presentation.screens.source.setup.shared.model

import kotlinx.serialization.Serializable

/**
 * How much of a source Android actually handed over. Partial is a lasting state rather than an
 * event — the photos branch keeps saying so at the top of every screen after it.
 */
@Serializable
enum class SourceAccessUi { Full, Partial }
