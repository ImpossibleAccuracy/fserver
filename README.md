# FServer

Cross-platform file exchange between a user's devices — over LAN and through a public server.
Modes: **sync**, **host**, **offload**. Open architecture: anyone can build their own client or server.

Full spec (Russian): [`docs/Terms of Reference.md`](docs/Terms%20of%20Reference.md).

## Layout

```
workspace/          Rust workspace — the portable part
  core/             pure Rust logic: transport, discovery, sync/offload engine, crypto, local index
  UniFFI/           adapters that expose core to clients and servers
bindings/           platform-specific code and the "official" apps
  clients/android/  Android client (Kotlin + Compose)
  clients/ios/      iOS client (planned)
  servers/          server implementations (planned)
docs/               design docs / spec
```

| Module | README |
|---|---|
| Rust core | [`workspace/core`](workspace/core/README.md) |
| UniFFI adapters | [`workspace/UniFFI`](workspace/UniFFI/README.md) |
| Bindings | [`bindings`](bindings/README.md) |
| Android client | [`bindings/clients/android`](bindings/clients/android/README.md) |

## Rules

- Logic lives in `workspace/core`. Bindings do UI and platform glue only.
- Core is platform-free: no OS/SDK dependency. Platform needs are core traits the binding implements.
- Server never trusts the client. Client-side checks are UX only; authorization is server-side.
- Protocol is versioned and negotiated at handshake. Old clients keep working.
- `evict` ≠ `delete`. Freeing local space must never propagate as a user deletion.

## Status

Early stage. Rust workspace is being scaffolded; Android client is the first binding.
