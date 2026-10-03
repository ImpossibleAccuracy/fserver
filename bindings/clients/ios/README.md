# iOS client

iOS client (planned). For now only the `FxCore` Swift package: the Rust engine exposed through the
generated UniFFI bindings ([`workspace/docs/crates.md`](../../../workspace/docs/crates.md)).

## Build

On macOS with Xcode, from `workspace/`:

```bash
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios   # once
cargo xtask bindings   # Swift sources + C headers
cargo xtask ios        # FxCoreFFI.xcframework
```

Then add `FxCore/` to the app as a local Swift package. Generated files are not committed.
