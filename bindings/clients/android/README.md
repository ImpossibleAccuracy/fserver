# Android client

Official Android client. Kotlin + Jetpack Compose.

## Modules

```
:app    Android app — UI, navigation, platform glue
:core   Android library wrapping the Rust core — generated uniffi bindings + the native .so
```

Shared logic comes from [`workspace/core`](../../../workspace/core/README.md) through
[`workspace/UniFFI`](../../../workspace/UniFFI/README.md). No sync/protocol logic here.

## Build

Always pass `--no-daemon`. Flavor matrix: `dev|prod` × `debug|release`.

```bash
./gradlew --no-daemon assembleDevDebug
./gradlew --no-daemon assembleProdRelease
./gradlew --no-daemon :app:testDevDebugUnitTest
./gradlew --no-daemon :app:lintDevDebug
```

`:core` has no hand-written source — its Kotlin bindings and its per-ABI `.so` are both
generated from `workspace/core` into `core/build/generated/`. The `:core:generateRustBindings`
task is wired into every variant, so a normal build regenerates them when the Rust changes.
That makes cargo and the Android NDK a hard requirement for building this project; see
[`workspace/core/docs/bindings.md`](../../../workspace/core/docs/bindings.md) for setup.

## Notes

- Dependencies go through `gradle/libs.versions.toml` — no hardcoded coordinates in build files.
- Gradle daemon runs JDK 25; modules compile to JVM 17, `minSdk` 24.
- `:core` ships only the ABIs listed in its `ndk.abiFilters`; that list mirrors the one in
  `workspace/core/generators/kotlin.sh`. Adding an ABI means editing both.
- AGP 9 has Kotlin built in — do not add `org.jetbrains.kotlin.android`, it is rejected.
- JNA must be the `@aar` variant. The plain jar ships JNA's native dispatch lib for desktop
  platforms only, so the jar resolves fine and then dies at runtime.
- Working notes for Claude Code: [`CLAUDE.md`](CLAUDE.md).
