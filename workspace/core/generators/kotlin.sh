#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../"

# Generated output is build output, not source — it lands under the Gradle build dir.
CORE_MODULE_DIR="../../bindings/clients/android/core"
GENERATED_DIR="$CORE_MODULE_DIR/build/generated"

KOTLIN_SRC_DIR="${UNIFFI_KOTLIN_OUT_DIR:-$GENERATED_DIR/kotlin/generateRustBindings}"
JNI_LIBS_DIR="${UNIFFI_JNI_LIBS_OUT_DIR:-$GENERATED_DIR/jniLibs/generateRustBindings}"

# Must match android.defaultConfig.ndk.abiFilters in core/build.gradle.kts
ABIS=(arm64-v8a armeabi-v7a x86_64)

if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "error: cargo-ndk not installed. Run: cargo install cargo-ndk" >&2
    exit 1
fi

# 1. Host build. uniffi-bindgen reads type metadata out of this .so,
#    so it must exist even though it never ships to a device.
cargo build --release --lib

# 2. Kotlin bindings. uniffi appends the package path from uniffi.toml
#    to --out-dir, so point it at the source root.
mkdir -p "$KOTLIN_SRC_DIR"
cargo run --features=cli --bin uniffi-bindgen -- generate \
    --library target/release/libnetwork_core.so \
    --language kotlin \
    --out-dir "$KOTLIN_SRC_DIR"

# 3. Cross-compiled .so, one per ABI, into jniLibs/<abi>/.
NDK_TARGETS=()
for abi in "${ABIS[@]}"; do
    NDK_TARGETS+=(-t "$abi")
done

cargo ndk "${NDK_TARGETS[@]}" -o "$JNI_LIBS_DIR" build --release --lib
