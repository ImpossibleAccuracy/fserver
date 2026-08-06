package com.fserver.app.presentation.navigation.scene

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.fserver.app.presentation.designkit.createBottomBarState
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy.Companion.bottomSheet

/** An [OverlayScene] that renders an [entry] within a [ModalBottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
private data class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val bottomSheetOptions: BottomSheetSceneStrategy.BottomSheetOptions,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = bottomSheetOptions.skipPartiallyExpanded,
        )

        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = sheetState,
            properties = modalBottomSheetProperties,
            sheetGesturesEnabled = bottomSheetOptions.sheetGesturesEnabled,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = if (bottomSheetOptions.showDragHandle) {
                { BottomSheetDefaults.DragHandle() }
            } else null,
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            },
        ) {
            CompositionLocalProvider(
                LocalLifecycleOwner provides lifecycleOwner,
                createBottomBarState(null) // No bottom bar on bottom sheet
            ) {
                entry.Content()
            }
        }
    }
}

/**
 * A [SceneStrategy] that displays entries that have added [bottomSheet] to their [NavEntry.metadata]
 * within a [ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val bottomSheetOptions = lastEntry.metadata[BottomSheetOptionsKey] ?: BottomSheetOptions()
        val bottomSheetProperties = lastEntry.metadata[BottomSheetPropertiesKey] ?: return null

        @Suppress("UNCHECKED_CAST")
        return BottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            bottomSheetOptions = bottomSheetOptions,
            modalBottomSheetProperties = bottomSheetProperties,
            onBack = onBack,
        )
    }

    data class BottomSheetOptions(
        val skipPartiallyExpanded: Boolean = false,
        val sheetGesturesEnabled: Boolean = true,
        val showDragHandle: Boolean = true,
    )

    companion object {
        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [ModalBottomSheet].
         *
         * @param properties properties that should be passed to the containing
         * [ModalBottomSheet].
         */
        fun bottomSheet(
            options: BottomSheetOptions = BottomSheetOptions(),
            properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
        ) = metadata {
            put(BottomSheetOptionsKey, options)
            put(BottomSheetPropertiesKey, properties)
        }

        private object BottomSheetOptionsKey : NavMetadataKey<BottomSheetOptions>
        private object BottomSheetPropertiesKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}
