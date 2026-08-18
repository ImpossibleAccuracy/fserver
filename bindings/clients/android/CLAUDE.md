# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repo.

## What this repo is

**Android half** of cross-platform file-exchange system (sync / host / offload between user devices,
over LAN and via public server). Server and other clients live elsewhere.

Design docs live in `../../../docs/`, in Russian. Use the **`project-docs` skill** to navigate
them — it has the spec section map and the RU→EN glossary. English grep over `../../../docs/`
silently finds nothing.

Load-bearing docs for `:net` work:

- `../../../docs/Connection Protocol.md` — target connection protocol: what is public vs behind
  auth, probe→connect flow, `AuthMethod` SPI, security invariants. Cross-platform — server and
  other clients follow it too, so changes there are not an Android-local decision.
- `docs/Handshake Migration.md` — Android-local, and the only doc under this repo. Staged delta
  from today's handshake to that protocol. Read before touching `:net/handshake`,
  `ConnectionManager`, or transport capabilities.

Project is **early-stage** now. Don't worry about versioning issues when making changes (but not
when doing code review).

## Module boundary (the one rule that matters)

```
:app           ──depends on──>  :core          (sync engine, protocol, crypto, local index)
:app           ──depends on──>  :core:storage  (default persistence backend)
:core:storage  ──depends on──>  :core          (implements its store SPI; :core never depends back)
```

- **`:core`** — Rust adapter module. Transport, discovery, sync/offload engine, crypto,local index.
  **Ships as standalone library**; other UIs (other platforms) consume it. Touches no disk itself.
- **`:core:storage`** — default persistence backend. Owns its own DataStore + SQLite, and the
  schema in them. Separate artifact, so a host that must control where the bytes live can drop it.
- **`:app`** — official Android client. UI + Android platform glue. Business logic belong in
  `:core`; urge to put sync/protocol logic in `:app` = signal `:core` interface missing something.

**`:core` public surface = `FServerCore` + `FServerConfig` + the storage SPI under `store/`.**
Everything else `internal`. Core wires itself with Koin in a *private* `koinApplication` container
— never the global `startKoin` context, and `coreModule` is not published. New capability = new
property on `FServerCore`, not a new exported class or Koin definition. Host re-publishes what it
needs (`app/di/CoreModule.kt`), so a UI on Hilt or hand-wiring works unchanged.

### Store ≠ repository

A **store** (`core/store/`) is the SPI: the narrowest set of calls the engine itself makes, shaped
for the engine. A **repository** (`:core:storage`) is UI-shaped and wider. They are deliberately
disjoint types — the engine never lists trusted devices, the UI never upserts one.

`:app` **never implements a store.** `@SubclassOptInRequired(FServerStorageApi::class)` on all four
turns that into a compile error; `:core:storage` is the one module that opts in, module-wide. If
you reach for `@OptIn` anywhere else, the repository you actually wanted is missing — add it to
`:core:storage`. A screen that needs engine state injects a repository, never a `store/` type.

Spec design constraints to keep in mind while writing `:core`:

- **Server never trusts client.** Client-side checks = UX affordance only; authorization is server
  job. Never treat client-side restriction as security control.
- **Engine interfaces must be replaceable.** MVP targets ~1000 files, so full-set comparison +
  SQLite index fine — but design must allow delta-sync swap-in later without rewrite.
- **Protocol versioned and negotiated at handshake.** Older clients must keep working.
- **`evict` ≠ `delete`.** In offload mode, freeing local space must not propagate as user deletion
  to backup device. Get this wrong → user data destroyed.

## Commands

Always pass `--no-daemon`.

```bash
# Build (flavor matrix: dev|prod × debug|release)
./gradlew --no-daemon assembleDevDebug
./gradlew --no-daemon assembleProdRelease

# net tests
./gradlew --no-daemon :net:test

# core tests
./gradlew --no-daemon :core:test
./gradlew --no-daemon :core:test --tests "com.fserver.core.SyncEngineTest"
./gradlew --no-daemon :core:test --tests "com.fserver.core.SyncEngineTest.evictDoesNotPropagateDelete"

# app tests
./gradlew --no-daemon :app:testDevDebugUnitTest
./gradlew --no-daemon :app:connectedDevDebugAndroidTest   # needs a device/emulator

# Android Lint
./gradlew --no-daemon :app:lintDevDebug
```

## Build setup notes

- Dependencies go through `gradle/libs.versions.toml` (version catalog) — no hardcoded coordinates
  in build files.
- Gradle daemon toolchain = JDK 25 (`gradle/gradle-daemon-jvm.properties`), but both modules compile
  to **JVM 17** and `minSdk` is 24. No APIs above those targets.

## Working in this repo

- **Prefer codegraph over grep.** `codegraph_explore` for architectural or "how does X work";
  `codegraph_search` to locate symbol. Returns verbatim source — usually replaces whole grep+read
  loop.
- **Ask questions.** User does not mind — clarifying question cheaper than wrong design, especially
  while architecture still being set.
- **Document decisions here, not details.** Add to this file when something *load-bearing* —
  boundary rule, protocol invariant, non-obvious constraint. No routine implementation notes.
- **Suggest skills/agents for recurring work.** Task repeats or encodes reusable knowledge (module
  scaffolding, protocol-change checklist, spec cross-referencing) → remind user to capture as skill
  or agent instead of re-deriving each session.
- Don't document obvious things/implementatin details into CLAUDE.md. Always ask user if they want
  to add something to CLAUDE.md before doing so.
- Avoid writing overly long comments and documentation. They can almost always be shortened to 1-2
  lines.
- There are TODOs list in docs directory. Anyone can add things to this doc.
