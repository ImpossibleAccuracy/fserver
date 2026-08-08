# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repo.

## What this repo is

**Android half** of cross-platform file-exchange system (sync / host / offload between user devices,
over LAN and via public server). Server and other clients live elsewhere.

Design docs live in `../../../docs/`, in Russian. Use the **`project-docs` skill** to navigate
them — it has the spec section map and the RU→EN glossary. English grep over `../../../docs/`
silently finds nothing.

Project is **early-stage** now.

## Module boundary (the one rule that matters)

```
:app  (Android, Compose UI)  ──depends on──>  :core  (sync engine, protocol, crypto, local index)
```

- **`:core`** — Rust adapter module. Transport, discovery, sync/offload engine, crypto,local index.
  **Ships as standalone library**; other UIs (other platforms) consume it.
- **`:app`** — official Android client. UI + Android platform glue. Business logic belong in
  `:core`; urge to put sync/protocol logic in `:app` = signal `:core` interface missing something.

Spec design constraints to keep in mind while writing `:core`:

- **Server never trusts client.** Client-side checks = UX affordance only; authorization is server
  job. Never treat client-side restriction as security control.
- **Engine interfaces must be replaceable.** MVP targets ~1000 files, so full-set comparison +
  SQLite index fine — but design must allow delta-sync swap-in later without rewrite.
- **Protocol versioned and negotiated at handshake.** Older clients must keep working.
- **`evict` ≠ `delete`.** In offload mode, freeing local space must not propagate as user deletion
  to backup device. Get this wrong → user data destroyed.

## Connection flow invariants

- **Every route to a device ends at `Destination.Pairing`.** Discovery list, QR scan, typed address
  — none of them establishes trust, so all three land on the same fingerprint confirmation. Adding
  a fourth detection route means routing it here too, not giving it its own connect screen.
- **A scanned code buys typing, not trust.** MVP QR payload is `{"ip":…,"port":…}` only, so QR goes
  through `DeviceDetectionRequest.ByManualAddress` like a typed address. Skipping fingerprint
  confirmation becomes legitimate *only* once the code actually carries the server key fingerprint
  (spec §3.2 config profile) — until then, don't.
- **`FoundDevice.kind` vs `FoundDevice.access` are unrelated.** `kind` is cosmetic (icon).
  `access` (Open/Password/Key) picks which fields the Pairing screen shows. Both are peer claims
  made before trust exists → presentation only, never authorization.

## Commands

Always pass `--no-daemon`.

```bash
# Build (flavor matrix: dev|prod × debug|release)
./gradlew --no-daemon assembleDevDebug
./gradlew --no-daemon assembleProdRelease

# core tests (plain JVM JUnit4)
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