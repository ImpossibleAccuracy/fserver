# Screen slice templates

Copy-paste skeletons for a screen named `Feature` at
`presentation/screens/feature/`. Imports omitted for brevity; follow the ones in
`screens/settings/security/` for a repository-fed screen or `screens/pairing/` for one with
effects and a destination key.

Note what is *absent*: no comments in any of these files. That is deliberate — see SKILL.md.

## model/FeatureState.kt

```kotlin
package com.fserver.app.presentation.screens.feature.model

import androidx.compose.runtime.Immutable

@Immutable
data class FeatureState(
    val items: List<ItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = items.isEmpty() && !isLoading

    @Immutable
    data class ItemUi(
        val id: String,
        val name: String,
        val subtitle: String?,
    )

    companion object {
        val SampleItems = listOf(
            ItemUi(id = "1", name = "MacBook-Pro.local", subtitle = "192.168.1.14:8384"),
        )
    }
}
```

Nested `*Ui` types live inside the State when only this screen uses them; shared ones go in
`presentation/composable/model/`.

## model/FeatureIntent.kt

```kotlin
package com.fserver.app.presentation.screens.feature.model

sealed interface FeatureIntent {
    data object Refresh : FeatureIntent
    data class ItemSelected(val id: String) : FeatureIntent
    data class QueryChanged(val query: String) : FeatureIntent
}
```

Note there is no `ItemClicked → navigate` intent: opening a detail screen is the screen's job, not
the ViewModel's. `ItemSelected` is here because this ViewModel has to *process* (e.g. connect) to the item before
anything can be shown — that work is what makes it an intent.

## model/FeatureUiEffect.kt

```kotlin
package com.fserver.app.presentation.screens.feature.model

sealed interface FeatureUiEffect {
    data object NavigateNext : FeatureUiEffect
    data class ShowMessage(val message: String) : FeatureUiEffect
}
```

Omit the file entirely when the screen has no one-shot events — which is most screens whose only
navigation is a plain tap.

## FeatureViewModel.kt

Locally-edited shape:

```kotlin
class FeatureViewModel(
    private val key: Destination.Feature,
    private val itemsRepository: ItemsRepository,
) : ViewModel() {
    private val effects = Channel<FeatureUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<FeatureState> = combine(
        itemsRepository.items(key.id),
        editable,
    ) { items, editable ->
        FeatureState(
            items = items.map(::itemUi),
            isLoading = editable.isLoading,
            error = editable.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeatureState(),
    )

    fun onIntent(intent: FeatureIntent) {
        when (intent) {
            FeatureIntent.Refresh -> refresh()
            is FeatureIntent.ItemSelected -> connect(intent.id)
            is FeatureIntent.QueryChanged -> editable.update { it.copy(query = intent.query) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            editable.update { it.copy(isLoading = true, error = null) }
            itemsRepository.refresh()
                .onFailure { t -> editable.update { it.copy(error = t.localizedMessage) } }
            editable.update { it.copy(isLoading = false) }
        }
    }

    private fun connect(id: String) {
        viewModelScope.launch {
            editable.update { it.copy(isLoading = true, error = null) }
            itemsRepository.connect(id).fold(
                onSuccess = { effects.send(FeatureUiEffect.NavigateNext) },
                onFailure = { t -> editable.update { it.copy(error = t.localizedMessage) } },
            )
            editable.update { it.copy(isLoading = false) }
        }
    }

    private fun itemUi(item: Item): FeatureState.ItemUi = FeatureState.ItemUi(
        id = item.id,
        name = item.displayName,
        subtitle = item.address,
    )
}

private data class Editable(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
```

`Editable` is file-private and holds only what this screen edits locally. Repository data is
`combine`d in and never copied into it — one source of truth per field.

Drop `key` when the destination carries no arguments, and drop `effects`/`uiEffects` when there
are no one-shot events.

## FeatureScreen.kt

```kotlin
@Composable
fun FeatureScreen(
    modifier: Modifier = Modifier,
    key: Destination.Feature,
    viewModel: FeatureViewModel = koinViewModel { parametersOf(key) },
    navigateToDetails: (String) -> Unit,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarController.current

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                FeatureUiEffect.NavigateNext -> navigateNext()
                is FeatureUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    FeatureScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToDetails = navigateToDetails,
        navigateUp = navigateUp,
    )
}

@Composable
private fun FeatureScreenContent(
    modifier: Modifier = Modifier,
    state: FeatureState,
    onIntent: (FeatureIntent) -> Unit,
    navigateToDetails: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.feature_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(state.items, key = { it.id }) { item ->
                FeatureRow(
                    item = item,
                    onClick = { navigateToDetails(item.id) },
                    onConnect = { onIntent(FeatureIntent.ItemSelected(item.id)) },
                )
                DkFadingDivider()
            }
        }
    }
}

@Composable
private fun FeatureRow(
    modifier: Modifier = Modifier,
    item: FeatureState.ItemUi,
    onClick: () -> Unit,
    onConnect: () -> Unit,
) {
    DkListRow(
        modifier = modifier,
        title = item.name,
        subtitle = item.subtitle,
        leading = { DkThumbnail(icon = Icons.Default.Devices) },
        trailing = {
            DkGhostButton(
                text = stringResource(R.string.action_connect),
                onClick = onConnect,
            )
        },
        onClick = onClick,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FeatureScreenPreview() {
    FServerTheme {
        FeatureScreenContent(
            state = FeatureState(items = FeatureState.SampleItems),
            onIntent = {},
            navigateToDetails = {},
            navigateUp = {},
        )
    }
}
```

The row shows both halves of the navigation rule side by side: tapping it opens a detail screen, so
it calls `navigateToDetails` directly and the ViewModel never hears about it; the "Connect" action
has work to do first, so it goes out as an intent and comes back as a `UiEffect`. Most screens only
have the first kind, and then `FeatureUiEffect.kt` doesn't exist at all.

The `LazyColumn` is **not** given horizontal padding: `DkListRow` carries the gutter inside its own
clickable node, so the row's ripple runs edge to edge while the text stays on the gutter. Give the
list `contentPadding` only when its children are inset surfaces (cards, `DkMediaTile` grids) rather
than full-bleed rows.

## Route.kt

```kotlin
package com.fserver.app.presentation.screens.feature

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.featureEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Feature> { key ->
        FeatureScreen(
            key = key,
            navigateToDetails = { id -> navigator.navigate(Destination.Settings.DeviceDetails(id)) },
            navigateNext = { navigator.navigate(Destination.Transfers) },
            navigateUp = navigator::navigateUp,
        )
    }
}
```

Drop the `key ->` lambda parameter for `data object` destinations.

This file is where every destination the screen can reach is spelled out, which is the practical
reason trivial taps stay out of the ViewModel: the whole navigation graph of a screen is readable
in one place, without cross-referencing a sealed interface of effects.

## Wiring diff

```kotlin
// presentation/model/Destinations.kt
@Serializable
data class Feature(val id: String) : Destination

// di/PresentationModule.kt
viewModelOf(::FeatureViewModel)

// App.kt, inside entryProvider { }
featureEntry(navigator)
```

## Overlays (bottom sheet / dialog destinations)

Mark the key `Destination.Overlay` instead of plain `Destination`. The navigator keeps the screen
underneath visible and leaves the bottom bar behaving as if that screen were still current;
`BottomSheetSceneStrategy` picks the presentation up from there. `screens/files/picker/` and
`screens/discovery/manual/` are the working examples.

Dialogs that belong to one screen (a rename prompt, a confirmation) are *not* destinations — keep
them in the screen's composition behind a `remember { mutableStateOf(...) }` editor enum, as
`SecurityScreen` does.
