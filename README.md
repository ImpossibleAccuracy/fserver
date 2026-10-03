# FServer

Cross-platform file exchange between a user's devices — over LAN and through a public server.

Project docs: [`docs/README.md`](docs/README.md).

## Layout

```
workspace/          Rust workspace — the portable part
  crates/fx-proto   shared types and wire format, no I/O
  crates/fx-net     transports, discovery, auth, sessions
  crates/fx-files   file access over every kind of location
  crates/fx-core    the engine: sync/offload, local index
  crates/fx-ffi     UniFFI adapter that exposes the engine to clients
bindings/           platform-specific code and the "official" apps
  clients/android/  Android client (Kotlin + Compose)
  clients/ios/      iOS client (planned)
  clients/desktop/  Windows/Linux desktop clients (planned)
  servers/          server implementations (planned)
docs/               design docs / spec
```

| Module          | README                                                           |
| --------------- | ---------------------------------------------------------------- |
| Rust workspace  | [`workspace/docs/crates.md`](workspace/docs/crates.md)           |
| Bindings        | [`bindings`](bindings/README.md)                                 |
| Android client  | [`bindings/clients/android`](bindings/clients/android/README.md) |

## Rules

- Logic lives in `workspace/crates/fx-core` and the crates below it. Bindings do UI and platform glue only.
- Core is platform-free: no OS/SDK dependency. Platform needs are core traits the binding implements.
