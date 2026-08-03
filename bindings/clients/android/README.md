# Android client

Official Android client. Kotlin + Jetpack Compose.

## Modules

```
:app    Android app — UI, navigation, platform glue
:core   local Kotlin module (currently empty; logic lives in workspace/core)
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

## Notes

- Dependencies go through `gradle/libs.versions.toml` — no hardcoded coordinates in build files.
- Gradle daemon runs JDK 25; modules compile to JVM 17, `minSdk` 24.
- Working notes for Claude Code: [`CLAUDE.md`](CLAUDE.md).
