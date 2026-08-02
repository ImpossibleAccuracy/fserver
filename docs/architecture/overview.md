# Architecture overview

> What the core is made of, how its parts depend on each other, what a host supplies, and the
> concurrency rules correctness depends on.

## 1. Layers

```
host (UI, platform glue)
   │  engine API + engine config + persistence SPI
   ▼
engine ── sources, sync, conflicts, encryption, one-shot transfers, export, journal
   │            │
   │            └──> file access   one file-system API over every kind of location
   ▼
network ── discovery, transports, handshake, authentication, sessions (message-agnostic)
   │
   └──> transport plug-ins (LAN TCP + mDNS, direct address, subnet sweep, radio transports)

persistence backend ── implements the engine's store SPI
```

Dependencies point down only. The network layer and the file access layer know nothing about sync;
the engine is the only part that knows what a source, a version or a conflict is.

| Layer        | Owns                                                                                  | Knows nothing about                          |
|--------------|---------------------------------------------------------------------------------------|----------------------------------------------|
| network      | finding peers, proving who they are, one sealed message pipe per peer                 | what the messages mean (opaque dictionary)   |
| file access  | scanning, reading, writing files in any location; canonical paths; plan strategies   | network, persistence, encryption             |
| engine       | everything the product does with files and peers                                      | where its state is persisted                 |
| persistence  | the schema of the engine's state                                                      | engine logic                                 |

Plan strategies ([planning](../sync/planning.md)) sit next to file access because they are pure
functions over two file lists, with no I/O.

## 2. Entry points

Each layer exposes exactly one runtime entry point; everything else is internal.

| Entry point     | Built from                                             | Gives                                                               |
|-----------------|--------------------------------------------------------|---------------------------------------------------------------------|
| network node    | network config: message dictionary, identity, transport plug-ins, policy | discovery, inbound sessions/requests, outbound probe/connect |
| files node      | platform handle, optional staging directory           | open a location as a file system, open staging                      |
| engine          | engine config + persistence                           | devices, sources, files, conflicts, encryption, one-shot, export, journal |

One network node serves exactly one message dictionary; two vocabularies mean two nodes.

### 2.1. Engine config - what the host supplies

| Field                | Meaning                                                                                          |
|----------------------|--------------------------------------------------------------------------------------------------|
| platform handle      | the platform trait bundle the engine needs (file locations, transports, keystore)               |
| background scope     | where background work runs; cancelled on shutdown                                                |
| time provider        | wall clock; injectable so tests and the HLC can control it                                       |
| eviction previewer   | optional hook to keep a preview before eviction frees bytes ([eviction](../sync/eviction.md))    |
| storage ciphers      | host ciphers on top of the built-in one ([at-rest encryption](../files/at-rest-encryption.md))   |

Persistence is supplied separately as the store SPI ([persistence](persistence.md)).

### 2.2. Startup

Creating the engine wires it, then in the background:

1. collects garbage - staged uploads nobody came back for ([housekeeping](../services/housekeeping.md));
2. migrates encryption - files a previous run did not bring in line with their source's policy;
3. resumes one-shot transfers - re-offers and re-sends unfinished outgoing ones.

**Nothing network-facing starts by itself.** The host explicitly starts:

- serving peers (refused while the OS withholds network access);
- presence - advertising and scanning ([discovery §4](../network/discovery.md#4-presence-policy));
- auto-sync and auto-accept ([sync pass §2](../sync/sync-pass.md#2-triggers),
  [discovery §6](../network/discovery.md#6-incoming-connections)).

A scan or advertiser started unasked turns a permission the user was never shown into "found
nothing".

### 2.3. Shutdown

Stop presence and coordinators → stop serving → shut down the network node (flushes `CLOSE` frames,
bounded by a 5 s grace) → cancel the background scope. The instance is dead afterwards.

## 3. Platform seams

The core is platform-free. Everything the OS provides is a trait the binding implements:

| Seam                                     | Provides                                                       | Doc |
|------------------------------------------|----------------------------------------------------------------|-----|
| transport / discovery / advertiser       | carrying frames, finding peers, being found                     | [discovery](../network/discovery.md) |
| file system per location kind            | scan, read, write, place, rename, delete                        | [file systems](../files/file-systems.md) |
| persistence stores                       | durable engine state                                            | [persistence](persistence.md) |
| identity key store                       | signs with the device's long-term key; the private key never leaves it | [authentication](../network/authentication.md) |
| key wrapping                             | wraps at-rest data keys under a key outside the app's data      | [at-rest encryption](../files/at-rest-encryption.md#8-keys) |
| preconditions                            | which permissions/radios an operation still needs - UX only, never a security control | - |

## 4. Concurrency rules that carry correctness

Violating any of these corrupts state:

- **One index lock per source.** Every read-modify-write of a source's local index rows holds it;
  two writers interleaving double-bump version vectors
  ([indexing §6](../sync/indexing.md#6-index-writes-outside-a-scan)).
- **One pass per source at a time, across both devices** - the sync lease
  ([sync pass §3](../sync/sync-pass.md#3-the-lease)).
- **The consumer of a session's inbound stream never blocks.** The network layer refuses frames
  while its consumer is not draining; slow work (disk writes of upload chunks) runs off the consumer
  ([sessions §4](../network/sessions.md#4-inbound-delivery),
  [transfers §4](../sync/transfers.md#4-receiver)).
- **Order-sensitive messages are handled in arrival order** (upload steps, lease acquire/release);
  everything else from a peer may run concurrently, at most 5 at a time per peer.
- **Destructive file operations are not cancellable once started** (delete, evict, move, place +
  index write), so a cancelled pass never leaves bytes and index disagreeing.

## 5. Where to read next

- How devices meet: [discovery](../network/discovery.md) →
  [connection protocol](../network/connection-protocol.md) →
  [authentication](../network/authentication.md) → [sessions](../network/sessions.md).
- How files sync: [sources](../sync/sources.md) → [indexing](../sync/indexing.md) →
  [versioning](../sync/versioning.md) → [planning](../sync/planning.md) →
  [sync pass](../sync/sync-pass.md) → [transfers](../sync/transfers.md) /
  [conflicts](../sync/conflicts.md) / [eviction](../sync/eviction.md).
