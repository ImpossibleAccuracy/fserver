# FServer

Cross-platform file exchange between a user's devices — over LAN and through a public server.

Project docs: [`docs/README.md`](docs/README.md).

## Layout

```
workspace/          Rust workspace — the portable part
  core/             pure Rust logic: transport, discovery, sync/offload engine, crypto, local index
  UniFFI/           adapters that expose core to clients and servers
bindings/           platform-specific code and the "official" apps
  clients/android/  Android client (Kotlin + Compose)
  clients/ios/      iOS client (planned)
  clients/desktop/  Windows/Linux desktop clients (planned)
  servers/          server implementations (planned)
docs/               design docs / spec
```

| Module          | README                                                           |
| --------------- | ---------------------------------------------------------------- |
| Rust core       | [`workspace/core`](workspace/core/README.md)                     |
| UniFFI adapters | [`workspace/UniFFI`](workspace/UniFFI/README.md)                 |
| Bindings        | [`bindings`](bindings/README.md)                                 |
| Android client  | [`bindings/clients/android`](bindings/clients/android/README.md) |

## Rules

- Logic lives in `workspace/core`. Bindings do UI and platform glue only.
- Core is platform-free: no OS/SDK dependency. Platform needs are core traits the binding implements.
