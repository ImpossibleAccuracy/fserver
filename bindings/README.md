# bindings

All platform-specific code and the "official" apps.

```
clients/android/   Android client
clients/ios/       iOS client
servers/           server implementations — planned
```

## Rules

- Everything here talks to the core through [`fx-ffi`](../workspace/docs/crates.md) and its generated bindings, never to the other crates directly.
- UI and platform glue only. Wanting to put sync/protocol logic in a binding means a core interface is missing something.
- Bindings are replaceable and independent. Third parties can write their own; these are just the official ones.
- Each binding builds with its own native toolchain (Gradle, Xcode, Cargo, …).
