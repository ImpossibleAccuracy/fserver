---
name: compose-mvi
description: House rules for writing Jetpack Compose UI and MVI screens in the :app module of this repo — the screen slice layout (State/Intent/UiEffect/ViewModel/Screen/Route), the Dk* design kit, Koin + Navigation 3 wiring, and the comment/modifier/ripple conventions. Use this skill whenever you add or edit anything under app/src/main/kotlin/com/fserver/app/presentation — a new screen, a new ViewModel, a new composable, a bottom sheet, a dialog, a list row, a preview — or when the request mentions Compose, MVI, a screen, UI state, intents, a ViewModel, navigation between screens, or the design kit. Also use it when reviewing UI code for convention drift.
---

# Compose + MVI in :app

Every screen in this app is the same six-file shape wired the same way. That sameness is the
point: a reader who knows one screen knows all of them, and a new screen costs no design
decisions. Follow the shape even when a screen looks too small to need it — a screen that starts
stateless usually doesn't stay that way, and retrofitting the slice later churns imports across
navigation, DI, and previews.

## The slice

```
presentation/screens/<feature>/
├── <Feature>Screen.kt        public wired composable + private stateless content + preview
├── <Feature>ViewModel.kt     state assembly + onIntent
├── Route.kt                  EntryProviderScope extension, one per screen
├── model/
│   ├── <Feature>State.kt     @Immutable data class, every field defaulted
│   ├── <Feature>Intent.kt    sealed interface — everything the user can do
│   └── <Feature>UiEffect.kt  sealed interface — one-shot only; omit if there are none
└── composable/               private sub-composables too big to keep in the screen file
```

Nested flows nest the directory (`screens/settings/security/`, `screens/source/mode/`), and the
entry function name flattens the path (`settingsSecurityEntry`, `sourceModeEntry`).

### Where a shared UI model lives

A `*Ui` type used by more than one screen goes at the **narrowest level that covers its users**,
and only widens when a real second group needs it:

```
screens/source/model/       shared by the source flow only
screens/settings/model/     shared by the settings subtree only
composable/model/           genuinely app-wide — fallback, not default
```

Group-level is the default because scope is what keeps these types honest. A `SourceModeUi` parked
in `composable/model/` reads as an app-wide concept, invites a settings screen to reference it, and
then the source flow can no longer change its own vocabulary without touching unrelated screens.
Kept in `screens/source/model/`, the flow owns it outright. Promote to `composable/model/` when a
second *group* actually needs it — a type the navigation keys carry across groups
(`SourceKindUi`, `DeviceKindUi`) belongs there from the start.

A type used by exactly one screen doesn't go in either place: nest it in that screen's State
(`FeatureState.ItemUi`), where its lifetime is obvious.

Read `references/templates.md` for the full copy-paste skeletons of all six files, and
`references/designkit.md` for the component catalog before you build any composable.

## Wiring a new screen — all four sites, or it won't compile/appear

1. `presentation/model/Destinations.kt` — add a `@Serializable` key under `Destination`. Carry
   arguments in the key itself; the flow keeps no state between screens. Make it
   `Destination.Overlay` if it draws on top (sheet, dialog) instead of replacing.
2. `Route.kt` in the slice — `fun EntryProviderScope<Destination>.<feature>Entry(navigator: AppNavigator)`.
   The entry is the only place that knows about `AppNavigator`; the screen takes plain lambdas.
3. `di/PresentationModule.kt` — `viewModelOf(::<Feature>ViewModel)`.
4. `App.kt` — call the entry function inside `entryProvider { }`, grouped with its section.

If the ViewModel needs the destination key, take it as the first constructor parameter and pass it
at the call site: `koinViewModel { parametersOf(key) }` (see `PairingScreen`/`PairingViewModel`).

## ViewModel

State is derived, never mutated field-by-field. Two shapes, both ending in `stateIn`:

- **Repository-fed**: `combine(repoA, repoB, …) { … }` mapping straight into the State.
- **Locally-edited**: a private `MutableStateFlow(Editable())` holding a file-private
  `Editable` data class, `.map { it.toPresentation() }` into the State. Mix in `combine` when
  some of it comes from repositories.

Always `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = State())`.
The 5s grace is what keeps a rotation from tearing down collectors and re-running discovery.

`onIntent(intent: X)` is one exhaustive `when` over the sealed interface with no `else` — the
compiler is what tells you a new intent went unhandled. Keep branches one-liners that delegate to
private suspend/helper functions; a `when` you have to scroll stops being a map of the screen.

One-shot events (navigate, snackbar) go through `Channel<UiEffect>(Channel.BUFFERED)` exposed as
`receiveAsFlow()`, never as state. State is a description of the screen right now, and a
"navigate" that survives in state fires twice on the next recomposition or process restore.

**Trivial navigation does not go through the ViewModel.** A tap that only means "open that screen"
— a list row leading to a detail, a button opening settings — calls the screen's navigation lambda
directly. Routing it through an intent, a `when` branch, a `Channel`, and a `UiEffect` adds five
hops of indirection and one enum case to move the tap that the `Route.kt` entry was already going
to handle. A `UiEffect` earns its place only when work happens *before* the navigation and decides
whether or when it occurs — click → connect → navigate on success, click → save → close. If the
ViewModel has nothing to do but relay the tap, it should not see the tap.

```kotlin
// Trivial: screen wires it straight to the lambda.
DkListRow(title = item.name, onClick = { navigateToDetails(item.id) })

// Non-trivial: the connect has to succeed first, so it is an intent + effect.
DkPrimaryButton(text = …, onClick = { onIntent(PairingIntent.Connect) })
```

Injection is constructor-only, via Koin. ViewModels depend on repositories from `:core` /
`:core:storage` and on app-owned stores in `data/` — never on a `com.fserver.core.store` type.

## Screen

Two composables, always:

```kotlin
@Composable
fun FeatureScreen(
    modifier: Modifier = Modifier,
    viewModel: FeatureViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                FeatureUiEffect.NavigateBack -> navigateUp()
            }
        }
    }

    FeatureScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}
```

The private content composable takes `state`, `onIntent`, and navigation lambdas — no ViewModel,
no Koin, no Android framework calls. That is what makes the `@Preview` at the bottom of the file
possible, and the preview is the only fast feedback loop this project has for UI.

End every screen file with a private `@Preview(showBackground = true, widthDp = 360, heightDp = 720)`
wrapping the content composable in `FServerTheme { }` with a hand-built sample state. Put reusable
sample data in a `companion object` on the State (`PairingState.SampleDevice`).

Screen-local UI that no one outside the composition cares about — which dialog is open, a scroll
position, a text field being edited — stays in the composition with `remember`. Routing it through
the ViewModel adds a round trip and an intent for something the ViewModel can't act on anyway.

## House rules

These are the ones that get missed. Existing files predate some of them, so match the rule, not
the neighbouring file.

**No comments in ViewModels, State/Intent/UiEffect classes, or screen files.** Not KDoc, not
inline. If a line needs explaining, the name is wrong or the logic belongs in a named private
function. The design kit (`designkit/`, `composable/`) is the exception — it is a shared library
surface consumed by every screen, so its KDoc stays and new components get the same treatment.

**`modifier: Modifier = Modifier` is the first parameter of every composable.** Callers reach for
it constantly, and a fixed position means never checking the signature. Every composable that
renders anything accepts one and applies it to its outermost node — a composable that swallows the
modifier can't be positioned by its caller and forces a wrapper `Box`.

**Reuse before you build.** Check `references/designkit.md` first. A row is `DkListRow`, a settings
row is `DkSettingsRow`/`DkSwitchRow`/`DkNavigationRow`, a card is `DkCard`, a button is
`DkPrimaryButton`/`DkSecondaryButton`/`DkGhostButton`. If the existing component is 90% right,
widen it with a defaulted parameter rather than forking a near-copy — two components that drift
apart is how a deck stops looking like one app. Only genuinely new shapes earn a new component,
and a new *shared* one goes in `designkit/` with KDoc and a preview.

**Spacing comes from `DkSpacing`, never a raw `.dp`.** Horizontal page gutter is
`DkSpacing.screenPadding` on every screen body.

**Clickable elements own their padding — mind the ripple.** The ripple traces the clickable node's
bounds, so where the padding sits relative to `.clickable` decides what the touch feedback looks
like:

- *Full-bleed row* (list rows, settings rows): `.fillMaxWidth().clickable().padding(horizontal = DkSpacing.screenPadding)`.
  The ripple reaches both screen edges while the text stays on the gutter. This is what `DkListRow`
  and `DkSettingsRow` already do — so put them in an **un-guttered** column and pad the surrounding
  blocks (section labels, info boxes) yourself.
- *Inset card / tile*: the parent applies `screenPadding` as `contentPadding` or on the column, and
  the clickable card sits inside it. The ripple stops at the card's rounded shape, which is right
  for a bounded surface.

What is always wrong is `.padding(…).clickable(…)` on a row: the touch target shrinks away from the
edges and the ripple floats in a box with a visible gap around it.

**All user-facing text is `stringResource(R.string.…)`.** Naming is `<feature>_<what>`, with shared
verbs under `action_` (`action_cancel`, `action_retry`).

**State classes are `@Immutable` data classes with a default for every field**, so
`initialValue = FeatureState()` works and previews stay cheap to write. Computed booleans the UI
needs (`canConnect`, `isEmpty`) belong on the State as `val … get()`, not recomputed in the
composable.

## Before you finish

- `./gradlew --no-daemon :app:assembleDevDebug` compiles.
- No comment survived in the ViewModel, model, or screen files.
- Every composable takes `modifier` first and applies it.
- The new screen is registered in all four wiring sites.
- The `@Preview` renders with a sample state.
