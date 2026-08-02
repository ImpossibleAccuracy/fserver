# bindings

All platform-specific code and the "official" apps.

```
clients/android/   Android client — Kotlin + Compose  (see its README)
clients/ios/       iOS client — planned
servers/           server implementations — planned
```

## Rules

- Everything here talks to the core through [`workspace/UniFFI`](../workspace/UniFFI/README.md), never to `workspace/core` directly.
- UI and platform glue only. Wanting to put sync/protocol logic in a binding means a core interface is missing something.
- Bindings are replaceable and independent. Third parties can write their own; these are just the official ones.
- Each binding builds with its own native toolchain (Gradle, Xcode, Cargo, …).
