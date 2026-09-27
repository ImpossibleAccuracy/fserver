# Design kit catalog

Two packages, both under `presentation/`:

- `designkit/` — the `Dk*` primitives. Generic, no app concepts, KDoc + `@Preview` on each.
- `composable/` — app-level shared UI (sheets, dialogs, the FAB/snackbar hosts) and the `*Ui`
  presentation models in `composable/model/`.

Check here before writing any composable. Everything below already exists.

## Layout & chrome

| Component | Signature (abridged) | Use for |
|---|---|---|
| `DkScaffold` | `(modifier, topBar, bottomBar, floatingActionButton, containerColor, content: (PaddingValues) -> Unit)` | Every screen root. Wraps M3 `Scaffold` and adds the global bottom-bar height to `innerPadding` so screen content is never hidden behind it. |
| `DkTopBar` | `(modifier, title: String, subtitle: String?, onBack: (() -> Unit)?, colors, actions)` | Flat top bar on the screen ground. `DKTransparentTopBarColors` for content that scrolls under it. |
| `DkActionBar` | `(modifier, content: ColumnScope.() -> Unit)` | Bottom action block above the nav bar. |
| `DkNavigationBar` / `RowScope.DkNavigationBarItem` | `(modifier, content)` / `(label, icon, selected, onClick, modifier)` | App bottom bar. |
| `DkFab` | `(modifier, icon, label: String?, onClick, visible)` | Screen FAB; reports its size so the global snackbar offsets correctly. `label != null` renders a `DkPillButton`. |
| `DkSnackbar` + `LocalSnackbarController` | `controller.showSnackbar(message)` | Transient messages. Read the controller from the composition local; don't host your own. |

## Rows & lists

| Component | Signature (abridged) | Use for |
|---|---|---|
| `DkListRow` | `(title, modifier, subtitle, subtitleStyle, subtitleMaxLines, dimmed, onClick, leading, trailing)` | Devices, files, folders. Gutter is inside the row — see the ripple rule. |
| `DkSettingsRow` | `(modifier, title, supportingText, accented, verticalAlignment, leadingIcon, onClick, trailing)` | Base settings row; the other three delegate to it. |
| `DkSwitchRow` | `(title, checked, onCheckedChange, modifier, supportingText, enabled, leadingIcon)` | Setting with a toggle. |
| `DkNavigationRow` | `(title, onClick, modifier, value, supportingText, accented)` | Setting that opens another screen; renders the chevron. |
| `DkValueRow` | `(title, value, modifier)` | Read-only value, mono-styled. |
| `DkSwitch` | `(checked, onCheckedChange, modifier, enabled)` | The switch alone, for rows that pair it with another control. |
| `DkTreeRow` | `(title, depth, modifier, expandable, expanded, trailingText, onClick, trailing)` | Directory tree with indent guides. |
| `DkStatusRow` | `(title, detail, state: DkCheckState, modifier)` | Requirement / diagnostic check line. |
| `DkMediaTile` | `(modifier, extensionLabel, durationLabel, remote, onClick)` | Square grid tile for media/files. |
| `DkThumbnail` | `(modifier, icon, contentDescription)` | 34dp leading square; keeps row height stable before previews load. |
| `DkFadingDivider` | `(modifier, color, inset)` | Divider between rows. Skip it after the last row. |

## Surfaces & content

| Component | Signature (abridged) | Use for |
|---|---|---|
| `DkCard` | `(modifier, outlined, onClick, content: ColumnScope.() -> Unit)` | Grouped block. `outlined = true` marks something needing attention without an error colour. |
| `DkCardKicker` / `DkCardTitle` / `DkCardMeta` | `(text, modifier)` / `(text, modifier)` / `(text, modifier, trailing)` | The three lines inside a `DkCard`. |
| `DkSectionLabel` | `(modifier, text, trailing)` | Heading above a group. Needs the gutter applied by the caller. |
| `DkCaption` / `DkMonoCaption` | `(modifier, text, textAlign)` / `(modifier, text)` | Secondary line, plain or monospaced. |
| `DkTag` | `(text, modifier, style: DkTagStyle)` | Small status pill. |
| `DkInfoBox` | `(text, modifier)` | Inline explanatory / warning box. |
| `DkFingerprintBlock` | `(modifier, groups: List<String>, columns)` | Key fingerprint, grouped for reading aloud. |
| `DkPlaceholderBox` / `DkSkeletonBlock` | `(label, modifier, dashedBorder, content)` / `(modifier, color)` | Unbuilt or not-yet-loaded area. Hatched so it never reads as an empty component. |
| `DkProgressBar` / `DkInlineSpinner` | `(progress, modifier)` / `(modifier)` | Determinate and indeterminate progress. |
| `DkPageIndicator` | `(pageCount, currentPage, modifier)` | Pager dots. |
| `DkStepBar` | `(modifier, stepCount, currentStep)` | Segmented progress of a fixed-length flow, under the top bar. |

## Input

| Component | Signature (abridged) | Use for |
|---|---|---|
| `DkPrimaryButton` | `(modifier, text, onClick, icon, enabled)` | The one action a screen is for. Outline + accent — the deck has no filled buttons. |
| `DkSecondaryButton` | same | Alternative action. |
| `DkGhostButton` | same + `danger` | Tertiary / inline action ("Change", "Cancel"); `danger` for decline/remove. |
| `DkPillButton` | `(modifier, text, onClick, icon)` | Floating pill over content. |
| `DkIconButton` | `(modifier, onClick, icon)` | Icon-only action. |
| `DkTextField` | `(label, value, onValueChange, modifier, singleLine, isPassword)` | Text input. |
| `DkSegmentedControl<T>` | `(options: List<DkSegmentedOption<T>>, selected, onSelect, modifier, labelsVisible)` | Small exclusive choice. |
| `DkChoiceBar<T>` | `(modifier, options: List<DkSegmentedOption<T>>, selected, onSelect)` | Value presets in one hairline box, selected cell filled. |

## Tokens

`DkSpacing`: `xxs 2` · `xs 4` · `sm 8` · `md 12` · `lg 16` · `xl 24` · `xxl 32` · `screenPadding 16`.
Never a raw `.dp` for layout spacing.

`DkType`: `mono` (11sp), `monoLarge` (12sp), `monoFingerprint` (14sp) — addresses, sizes, protocol
details. Everything else comes from `MaterialTheme.typography`.

Modifiers/brushes: `Modifier.dkDashedBorder(color, cornerRadius, dash)` for anything provisional,
`Modifier.dkHatch(color, stripe)` for placeholder fills, `dkFadingBrush(color, inset)` for rules
that fade at both ends.

Colors always come from `MaterialTheme.colorScheme`. Previews wrap in `FServerTheme { }`, and
design-kit previews additionally use `DkSurfacePreview { }` so outline-first components stay
visible against the ground.

## Presentation models

`composable/model/` holds the app-wide `*Ui` types — `DeviceKindUi`, `SourceKindUi`,
`AuthMethodUi`, `RequirementUi`, `TransportKindUi`, plus their `icon` / label extensions. When a
screen needs an icon or a display label for a `:core` enum, the mapping goes here, not inline in
the composable, so two screens can't disagree about what a device kind looks like.

This directory is the **fallback**, not the default. A `*Ui` shared by one screen group belongs to
that group — `screens/source/model/`, `screens/settings/model/` — and only moves up when a second
group genuinely needs it. A type used by a single screen nests inside that screen's State instead.
See "Where a shared UI model lives" in SKILL.md.
