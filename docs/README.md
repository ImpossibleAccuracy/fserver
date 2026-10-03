# FServer design documentation

Specification of the engine: what it is and how it must behave, independent of any platform or
implementation. Every client and server core implements it. Each document owns one topic and links
to the others instead of repeating them.

The authoritative product spec is [terms-of-reference.md](terms-of-reference.md) (ToR). When a
document and the ToR disagree, the ToR wins and the document is fixed. When an implementation and a
document disagree, the implementation is wrong.

## Map

| Folder | Document | Answers |
|--------|----------|---------|
| - | [terms-of-reference.md](terms-of-reference.md) | what the product must do, trust model, roadmap |
| `architecture/` | [overview.md](architecture/overview.md) | layers, entry points and engine config, startup, platform seams, concurrency rules |
| | [persistence.md](architecture/persistence.md) | what is persisted, store contracts, what is in memory only |
| `network/` | [discovery.md](network/discovery.md) | transport/discovery/advertiser plug-ins, LAN and radio transports, presence policy, reaching a known device, access codes |
| | [connection-protocol.md](network/connection-protocol.md) | disclosure levels, probe → connect, handshake frames, wire format, fragmentation, ports |
| | [authentication.md](network/authentication.md) | auth methods, identity proof, trust gate and pinning, session crypto, throttling |
| | [sessions.md](network/sessions.md) | session lifecycle, queues, request/response, keep-alive, reconnect, crossed dials, config reload |
| `files/` | [file-systems.md](files/file-systems.md) | location kinds, the `FileSystem` API, atomic placement, canonical paths, file id |
| | [at-rest-encryption.md](files/at-rest-encryption.md) | per-source encryption policy, sealed file format, migration, keys |
| `sync/` | [sources.md](sync/sources.md) | source model, roles, modes and their rules, setup exchange, removal, authorization |
| | [indexing.md](sync/indexing.md) | change detection, ignore rules, lazy hashing, every index write |
| | [versioning.md](sync/versioning.md) | version vectors, HLC, peer clock skew |
| | [planning.md](sync/planning.md) | strategies per mode, action verbs, move pairing, file limits, action guard |
| | [sync-pass.md](sync/sync-pass.md) | triggers, the sync lease, one pass step by step, the answering side |
| | [transfers.md](sync/transfers.md) | upload protocol, resume, staging, verification, downloads |
| | [conflicts.md](sync/conflicts.md) | Ask vs last-write-wins, decisions, keep both |
| | [eviction.md](sync/eviction.md) | evict ≠ delete, confirmation, pins, fetch on demand, previews |
| `services/` | [one-shot-transfers.md](services/one-shot-transfers.md) | sending files once, outside any source |
| | [data-export.md](services/data-export.md) | export archive contents and layout |
| | [housekeeping.md](services/housekeeping.md) | garbage collection, activity journal and issues |

Suggested reading order for a port: overview → persistence → network (discovery, protocol,
authentication, sessions) → files → sync (sources, indexing, versioning, planning, sync pass,
transfers, conflicts, eviction) → services.

## Glossary

| Term | Meaning |
|------|---------|
| **source** | one location on this device paired with one peer under one mode; both devices hold it under the same id |
| **initiator / follower** | the device that registered a source / the one that accepted hosting it |
| **driver** | the device that runs passes for a source: both in Mirror, the initiator otherwise |
| **pass** | one run that brings a source in line with its mode, under a lease |
| **lease** | the right to run a pass over a source, granted by the peer; prevents concurrent passes |
| **index** | per-source table of files this device has worked through (local index) / the peer's last report (remote index) |
| **tombstone** | an index row in state `Deleted`; also the record left by a removed source |
| **evict** | free local bytes, keep the file in the set - never a deletion |
| **pin** | exempt a file from eviction |
| **version** | (version vector, HLC, origin device) of a file |
| **HLC** | hybrid logical clock; breaks ties between concurrent versions, never orders them |
| **canonical path / file id** | the cross-device form of a file's path / SHA-256 of it |
| **locator** | a backend's own address of a file on this device; never leaves it |
| **location** | where a source's files live on this device (directory, OS-granted folder, media library, app storage…) |
| **staging** | cache space where incoming bytes wait until whole |
| **probe / greeting** | public pre-auth exchange of versions and auth methods |
| **pin (trust)** | a remembered peer key with the strongest method it authenticated with |
| **SAS** | short authentication string the users compare |
| **one-shot transfer** | sending files to a device once, outside any source |

## Conventions for these docs

- English, Markdown, numbered sections, a version/status note near the top where useful.
- **Normative**: describe what the system is and how it must behave - behavior, invariants, wire
  formats, constants. No platform APIs, no module or class names of a particular implementation, no
  "currently" or "known gaps": where the right behavior was not obvious, the document states the
  decision.
- One topic per document; link instead of repeating. A doc that starts explaining another doc's
  topic should link to it.
- Platform specifics appear only as seams ("a platform keystore", "an OS-granted folder handle").
- Open design decisions (none can be implemented before they are settled) are marked as such in the
  section that needs them.
- **Implementation docs live with the implementation.** A platform part (the Rust workspace, a
  client, a server) may keep its own documentation in its own folder - module layout, build,
  dependency rules - for example [`workspace/docs/`](../workspace/docs/crates.md). Such docs describe
  how that implementation follows these documents; they never restate or override them.
