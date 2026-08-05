## Generating Kotlin bindings

### Setup

Rust 1.85+ (edition 2024). Verified on 1.97.1. That is all you need to build the crate
or run the CLI:

```bash
cargo build
cargo run          # calculator CLI in src/main.rs
```

Generating the Kotlin bindings needs three more things:

| What            | Install                                                                                |
| --------------- | -------------------------------------------------------------------------------------- |
| Android targets | `rustup target add ...` |
| `cargo-ndk`     | `cargo install cargo-ndk`                                                              |
| Android NDK     | Android Studio → SDK Manager → SDK Tools → **NDK (Side by side)**                      |

`cargo-ndk` locates the NDK through `ANDROID_NDK_HOME`, or `$ANDROID_HOME/ndk/<version>`.
Export one of them if the build cannot find it.

Optional: `ktlint` on `PATH`. Without it bindgen prints a warning and leaves the generated
Kotlin unformatted — harmless, since nobody reads that file.

### Run generator

Normally you do not. The Android build runs it for you — `:core` wires the generator into
every variant, so `./gradlew :app:assembleDevDebug` regenerates whatever is stale first.
To force it:

```bash
./gradlew :core:generateRustBindings      # from bindings/clients/android
./generators/kotlin.sh                    # or standalone, from workspace/core
```

Three steps:

1. Host `cargo build --release --lib`. `uniffi-bindgen` reads the exported type metadata
   straight out of this `.so`, so it must exist even though it never ships to a device.
2. `uniffi-bindgen generate` → `core/build/generated/kotlin/generateRustBindings/com/fserver/library/core/network_core.kt`
3. `cargo ndk` → `core/build/generated/jniLibs/generateRustBindings/<abi>/libnetwork_core.so`,
   for `arm64-v8a`, `armeabi-v7a`, `x86_64`

Both land under the Gradle build dir — generated code is build output, never committed and
never edited. `:core` has no hand-written source at all.

Notes:

- `uniffi.toml` holds the bindings config (Kotlin package name, Android object cleaner).
- AGP owns those two output paths and hands them to the task; the script's defaults mirror
  them so a standalone run writes where Gradle expects. Gradle overrides both through
  `UNIFFI_KOTLIN_OUT_DIR` / `UNIFFI_JNI_LIBS_OUT_DIR`.
- The ABI list in the script must stay in sync with `ndk.abiFilters` in
  [`core/build.gradle.kts`](../../bindings/clients/android/core/build.gradle.kts).
- The `uniffi-bindgen` binary sits behind the `cli` feature, so plain `cargo build` and
  `cargo run` ignore it. That is what keeps the CLI in `src/main.rs` runnable alongside it.
