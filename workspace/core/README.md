# core

Project root logic. Pure Rust, no platform code.

## What lives here

- device discovery (LAN)
- transport, abstracted from connection type
- sync / host / offload engine
- crypto (at-rest + transport)
- local index and state (metadata, versions)
- protocol types and version negotiation

## What does not

- UI
- OS/platform APIs (filesystem layout, permissions, background execution) — declared as traits here, implemented by a binding
- FFI glue — that is [`../UniFFI`](../UniFFI/README.md)

## Rules

- No dependency on any client or server crate. Core knows nothing about who calls it.
- Public API is a stable, versioned surface. Making something `pub` is a commitment.
- Engine interfaces stay replaceable — MVP does full-set comparison + a local index at ~1000 files; delta sync must be swappable in later without a rewrite.

Consumed by [`../UniFFI`](../UniFFI/README.md); everything else consumes it through that.
