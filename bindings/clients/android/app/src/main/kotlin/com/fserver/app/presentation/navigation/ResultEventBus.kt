package com.fserver.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.reflect.KClass

/**
 * One-off results handed from the screen that produced them back to the screen that asked.
 *
 * A screen that answers a question for someone else - pick a device, pick a folder - has no
 * business knowing who asked or what they do next. It [sendResult]s and pops; whoever wanted the
 * answer picks it up with [ResultEffect] when it comes back on screen.
 *
 * Results are kept per type and carry the id they were published under, which is what keeps an
 * answer meant for one screen from firing on another: see [ResultEffect].
 */
@Stable
class ResultEventBus {
    private val pending = mutableStateMapOf<String, Pair<Int, Any>>()

    private var counter by mutableIntStateOf(0)

    fun <T : Any> sendResult(result: T) {
        counter += 1
        pending[keyOf(result::class)] = counter to result
    }

    /** The id of the last result published, of any type. */
    fun lastId(): Int = counter

    /** The id the standing result of [type] was published under, or 0 when there is none. */
    fun pendingId(type: KClass<*>): Int = pending[keyOf(type)]?.first ?: 0

    fun pendingValue(type: KClass<*>): Any? = pending[keyOf(type)]?.second

    private fun keyOf(type: KClass<*>): String = type.qualifiedName ?: type.toString()
}

val LocalResultEventBus = staticCompositionLocalOf<ResultEventBus> {
    error("No LocalResultEventBus: the bus is provided around NavDisplay.")
}

@Composable
fun rememberResultEventBus(): ResultEventBus = remember { ResultEventBus() }

/**
 * Runs [onResult] for every [T] published after this screen first composed.
 *
 * The baseline is the point: a result nobody consumed stays on the bus, and without it the next
 * screen to listen for that type would pick up an answer to a question it never asked. Only an id
 * higher than the one standing when this screen appeared is this screen's.
 */
@Composable
inline fun <reified T : Any> ResultEffect(crossinline onResult: (T) -> Unit) {
    val bus = LocalResultEventBus.current
    var seen by rememberSaveable { mutableIntStateOf(bus.lastId()) }
    val id = bus.pendingId(T::class)

    LaunchedEffect(id) {
        if (id <= seen) return@LaunchedEffect
        seen = id
        (bus.pendingValue(T::class) as? T)?.let { onResult(it) }
    }
}
