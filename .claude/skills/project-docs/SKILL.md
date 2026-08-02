---
name: project-docs
description: Navigate this repo's design documentation in docs/ — the authoritative spec (ToR) and the normative engine specs every implementation follows (network protocol, auth, sync, indexing, versioning, conflicts, transfers, eviction, encryption, file systems).
  Use it whenever you need to know what the product or engine is supposed to do, why something is designed the way it is, what a mode or term means,
  what roadmap stage something belongs to, before designing or porting anything non-trivial in :core, :net, :files or workspace/core,
  and whenever you add or edit a doc under docs/.
---

# Project docs

`docs/` holds the design documentation, in **English**. `docs/README.md` is the index: a map of
every document, a glossary and the writing conventions. Read it first - it is short.

Codegraph indexes code symbols, not prose, so `codegraph_*` tools find nothing in `docs/`. Use
`Grep` and `Read` there.

## Layout

```
docs/
  README.md                 index, glossary, conventions
  terms-of-reference.md     the spec (ToR) - wins on intent
  architecture/             overview (modules, entry points, startup, concurrency rules), persistence
  network/                  discovery, connection-protocol, authentication, sessions
  files/                    file-systems (locations, paths, file id), at-rest-encryption
  sync/                     sources, indexing, versioning, planning, sync-pass, transfers, conflicts, eviction
  services/                 one-shot-transfers, data-export, housekeeping (GC + journal)
```

## Which doc answers what

| Question                                                    | Doc |
|-------------------------------------------------------------|-----|
| what must the product do, trust model, roadmap stage        | `terms-of-reference.md` |
| how layers fit, entry points, engine config, startup        | `architecture/overview.md` |
| what is persisted, store contracts                          | `architecture/persistence.md` |
| how peers are found, transports, reaching a known device    | `network/discovery.md` |
| handshake frames, wire format, what is public               | `network/connection-protocol.md` |
| auth methods, identity proof, pinning, session crypto       | `network/authentication.md` |
| session queues, reconnect, crossed dials                    | `network/sessions.md` |
| location kinds, canonical path, file id, atomic placement   | `files/file-systems.md` |
| at-rest encryption                                          | `files/at-rest-encryption.md` |
| source model, modes and per-role rules, setup, removal      | `sync/sources.md` |
| when a local change becomes a version, hashing              | `sync/indexing.md` |
| version vectors, HLC, clock skew                            | `sync/versioning.md` |
| what the planner decides per mode                           | `sync/planning.md` |
| triggers, lease, pass steps, answering side                 | `sync/sync-pass.md` |
| upload/download protocol, resume, staging                   | `sync/transfers.md` |
| Ask vs LWW                                                  | `sync/conflicts.md` |
| evict vs delete, pins, fetch on demand                      | `sync/eviction.md` |

## Reading rules

1. **The docs are the target, not a description of the code.** They are normative and
   platform-free. ToR wins over a topic doc; a topic doc wins over any implementation - where the code
   behaves differently, the code is wrong (say so, don't "fix" the doc to match it).
2. **Get a doc's shape first** - `grep -n '^#\{1,4\} ' docs/<path>.md` gives a heading map with line
   numbers, then `Read` with `offset`/`limit`.
3. **Read the surrounding section, not just the hit line.** These docs qualify statements heavily; a
   rule often has an exception a few lines below.
4. **Follow links instead of guessing.** Docs do not repeat each other; a topic mentioned in passing
   is owned by the linked doc.

## Adding or editing docs

- English, Markdown, numbered sections, a version/status note at the top where useful.
- **Normative and platform-free**: what the system is and how it must behave. No platform APIs
  (no Android/iOS/desktop specifics beyond naming a seam like "a platform keystore"), no module or
  class names of an implementation, no "Reference:" lines, no "currently"/"known gaps" sections - when
  the right behavior is not obvious, decide it (ask the user if it is a product decision) and write
  it down. Implementation TODOs go to the platform's own TODO list, not here.
- **One topic per document; never mix.** Change detection is `indexing`, ordering is `versioning`,
  resolution is `conflicts` - a doc that starts explaining another doc's topic links to it instead.
- Put a new doc in the folder of its layer; add it to the map in `docs/README.md` (and the table
  above). Terms that a reader would not know go into the glossary.
- Describe behavior, invariants, wire formats, constants. Skip trivia and class-by-class
  descriptions.
- Protocol docs (`network/`, wire formats anywhere) are cross-platform: a change there binds the
  server and every client.
- Keep links relative and check anchors: GitHub slugs lowercase the heading and drop punctuation
  (`### 4.1. Admission (\`Init\`)` → `#41-admission-init`).
- Code comments cite docs by file name and section (`connection-protocol.md §4.7`,
  `at-rest-encryption.md §6.1`); renaming a doc or renumbering a section means grepping the code
  for the old reference.
