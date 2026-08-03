# UniFFI

Adapters that expose [`core`](../core/README.md) to clients and servers.

## What lives here

- UniFFI interface definitions for the core API
- type mapping between Rust core types and the foreign-language boundary
- generated-binding setup for the target platforms (Kotlin, Swift, …)

## What does not

- business logic — belongs in `core`
- platform/UI code — belongs in [`../../bindings`](../../bindings/README.md)

## Rules

- Thin layer. If something here does more than translate, it is in the wrong module.
- The exported surface is the documented local API between core and UI — versioned, changed deliberately.

Bindings consume the artifacts produced here, never `core` directly.
