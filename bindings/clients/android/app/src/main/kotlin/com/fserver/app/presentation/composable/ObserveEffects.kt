package com.fserver.app.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** Collects a ViewModel's one-shot effects for as long as the screen is composed. */
@Composable
fun <T> ObserveEffects(effects: Flow<T>, onEffect: suspend (T) -> Unit) {
    LaunchedEffect(effects) { effects.collect { onEffect(it) } }
}
