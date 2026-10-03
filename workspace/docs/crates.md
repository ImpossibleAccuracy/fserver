# Rust workspace: crates and rules

> Implementation document of the Rust core (`workspace/`). The behavior it implements is specified
> platform-free in [`docs/`](../../docs/README.md); where this code and those docs disagree, the code
> is wrong.

## 1. Crates

| Crate        | Role                                                                                                             | Layer ([overview §1](../../docs/architecture/overview.md#1-layers)) |
|--------------|------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `fx-proto`   | shared types: ids, content hash, version vector, HLC. No I/O                                                     | protocol                                                            |
| `fx-net`     | transports, TLS, discovery, pairing, authentication, sessions, streaming transfer                                | network                                                             |
| `fx-files`   | the platform `FileSystem` seam, scanning, hashing, atomic placement, at-rest crypto, placeholders, media, export | file access                                                         |
| `fx-core`    | the engine: sources, sync, conflicts, host/offload modes, the SQLite index, local API                            | engine                                                              |
| `fx-ffi`     | UniFFI adapter: exports `fx-core` to Kotlin/Swift, platform seams as foreign traits                              | host boundary                                                       |
| `fx-testkit` | fakes of platform seams for tests (dev-dependency only)                                                          | -                                                                   |

Tooling: `uniffi-bindgen/` (binding generator, library mode) and `xtask/` (`cargo xtask …`).

## 2. Dependency graph

```
            fx-proto
           ↗        ↖
      fx-net        fx-files
           ↖        ↗
            fx-core
               ↑
             fx-ffi  ──>  Kotlin / Swift

fx-testkit → fx-proto, fx-net, fx-files      (dev-dependency of fx-core)

arrows point from a crate to what it depends on
```

## 3. Rules

1. **The graph is one-way.** `fx-proto ← fx-net`, `fx-proto ← fx-files`,
   `{fx-proto, fx-net, fx-files} ← fx-core ← fx-ffi`. `fx-net` and `fx-files` never depend on each
   other: transfers in `fx-net` work on `AsyncRead`/`AsyncWrite`, and `fx-core` binds a stream to a
   file.
2. **Only `fx-ffi` depends on `uniffi`.** Other crates are plain Rust APIs without uniffi
   attributes. Types and errors are converted in `fx-ffi` through `From`.
3. **Platform capabilities are traits.** File access, background work and permissions are declared
   as traits in `fx-files`/`fx-net` and exported by `fx-ffi` as foreign traits (`#[uniffi::export(with_foreign)]`) for
   Kotlin/Swift to implement. No `#[cfg(target_os)]` in
   business logic.
4. **No crate is called `core`** - it clashes with Rust's built-in `core` crate.
5. **The async runtime is private.** `Engine` creates its tokio runtime and never exposes it.

Also enforced by the workspace:

- All dependency versions live in `[workspace.dependencies]`; crates use `dep = { workspace = true }`.
- Libraries report errors through `thiserror`. `unwrap()`/`expect()` outside tests are denied by
  clippy (`[workspace.lints]`).
- Dependency licenses are checked with `cargo deny check` (`deny.toml`).

## 4. Commands

Run from `workspace/`:

| Command                                                 | Does                                                                                                                                                              |
|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `cargo build --workspace`                               | builds everything                                                                                                                                                 |
| `cargo test --workspace`                                | runs tests                                                                                                                                                        |
| `cargo clippy --workspace --all-targets -- -D warnings` | lints                                                                                                                                                             |
| `cargo fmt --check`                                     | checks formatting                                                                                                                                                 |
| `cargo deny check`                                      | checks licenses, advisories, sources                                                                                                                              |
| `cargo xtask bindings`                                  | builds `fx-ffi` for the host and generates Kotlin into `bindings/clients/android/core/build/generated/uniffi/kotlin` and Swift into `bindings/clients/ios/FxCore` |
| `cargo xtask android [--release]`                       | builds `libfx_ffi.so` for Android ABIs with cargo-ndk into `bindings/clients/android/core/build/generated/jniLibs`                                                |
| `cargo xtask ios [--release]`                           | macOS only: builds iOS static libraries and packs `FxCoreFFI.xcframework`                                                                                         |

Generated code is never committed.
