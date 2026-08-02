# Planning: from two indexes to a list of actions

> The pure decision step of a pass: given what both sides hold, what has to happen to each file.
>
> How a pass executes the plan: [sync pass](sync-pass.md). How `Conflict` actions are resolved:
> [conflicts](conflicts.md).

## 1. Shape

A strategy is a **pure function** `plan(params, snapshot) -> actions`: no network, no disk, no side
effects. The caller collects the snapshot, plans, and executes; at the ~1000-file scale a full
comparison per pass is fine (ToR §4.1), and the interface stays replaceable by a delta planner.

```
FilesSnapshot { local: [FileRecord], remote: [FileRecord], now }
FileRecord    { id (file id), path, locator? (local only), state, content (hash?), size, lastModified,
                lastAccessed? (local only), version? }
```

Records are joined by file id; each pair `(local?, remote?)` is decided independently, then
`pairMoves` post-processes the list.

Only the device that **drives** the source plans ([sources §3.1](sources.md#31-what-each-end-may-do)).

## 2. Mode → strategy

| Mode         | Strategy  | Params                                                                    |
|--------------|-----------|---------------------------------------------------------------------------|
| `Mirror`     | Mirror    | -                                                                         |
| `AutoUpload` | One-way   | `propagateDeletions = true`, `skipModifiedBefore = ignoreFilesBefore`     |
| `Offload`    | One-way   | `propagateDeletions = false`, `evictWhen = policy`                        |
| `Host`       | Hosted    | -                                                                         |

## 3. Building blocks

- **Causality**: `compare(local.vector, remote.vector)`, a missing version = empty vector
  ([versioning §3](versioning.md#3-version-vector-operations)).
- **Content match**: both hashes known → `SAME`/`DIFFERENT`; else sizes differ → `DIFFERENT` (a size
  change is certain even unhashed); else `UNKNOWN`.
- **Merge if diverged**: same content, vectors not `Equal` → `MergeVersion(merge of both)`. Not a
  conflict - both sides just record both histories.
- **Hash before deleting**: an unhashed `Present` file about to be deleted may hold an edit its
  version does not show yet → `ComputeHash` first, re-plan with what it finds (ToR §3.4: "until the
  hash is computed, the file is neither overwritten nor deleted").
- **Evictable**: `Present`, not pinned, not a copy fetched on demand (those expire by TTL, never by
  a plan).
- **Dominating version** (one-way only): the version that makes the winner newer than both - its own
  if already `Newer`, else the merge of both histories.

## 4. Verbs

| Action           | Meaning                                                                                  |
|------------------|------------------------------------------------------------------------------------------|
| `Upload(file, version)`   | send local bytes; the peer records `version`                                    |
| `Download(file, version)` | pull remote bytes; recorded here as `version` (default: the remote's)           |
| `DeleteRemote(file, version?)` | propagate a deletion; the peer records `version` or a new own one          |
| `DeleteLocal(file, version)` | apply the peer's deletion here under its version                             |
| `MoveRemote(from, to, version, deletedVersion)` | the peer renames its `from` to `to`'s path instead of receiving bytes |
| `MoveLocal(...)` | the same, here                                                                           |
| `EvictLocal(file)` | free local bytes, keep the file in the set - **never** a deletion                      |
| `ComputeHash(local, remote)` | blocked on an unknown hash: hash the side(s) with bytes, then re-plan        |
| `MergeVersion(local, remote, version)` | same content, both sides record `version`; no bytes move           |
| `Conflict(local, remote)` | concurrent change; the strategy refuses to guess                                |

Every action carries the records it was decided from - executors re-check them against the current
index before acting (§8). `reason` is diagnostics only; nothing branches on it.

`ComputeHash` is a verb rather than an assumption because both assumptions are wrong: "unhashed means
equal" skips real changes, "unhashed means different" re-uploads the whole set every scan.

## 5. Move pairing

A rename reaches a plan as a new file (new id) plus a deleted one. After deciding, `pairMoves`:

- `Upload(X)` + `DeleteRemote(Y)` where `Y` is `Present` remotely with a known hash equal to `X`'s →
  `MoveRemote(from=Y, to=X)`: the peer renames its copy; no bytes travel;
- `Download(X)` + `DeleteLocal(Y)` with `Y` present here (has a locator) and the same hash →
  `MoveLocal`.

Pairs match by content hash, one-to-one. Both sides then record the target under the upload/download
version and the source under the deletion version. The indexer's rename-candidate hashing exists to
make these hashes known ([indexing §5](indexing.md#5-lazy-hashing)).

## 6. Decision tables

Notation: `L`/`R` = local/remote record; states `P`resent, `E`victed, `D`eleted, `-` absent;
causality is `L` relative to `R`.

### 6.1. Mirror

| L | R | Decision |
|---|---|----------|
| - | P | `Download` |
| - | E / D | nothing (evicted there: its bytes are on a backup a deletion would reach) |
| P | - | `Upload` |
| E / D | - | nothing |
| D | D | `MergeVersion` if diverged |
| D | P/E | `Newer`: hash-before-delete, then `DeleteRemote`; `Older`: receive newer (below); `Equal`/`Concurrent`: `Conflict`. If the survivor is evicted, only a **newer deletion** may act |
| P/E | D | mirror image of the row above (`DeleteLocal`, send newer, `Conflict`) |
| P/E | P/E | content `SAME` → merge if diverged. Both evicted → nothing. `UNKNOWN` and every unhashed side still has bytes → `ComputeHash`. One side evicted and vectors `Equal` → nothing (the evicted side catches up on demand). Else by causality: `Newer` → send newer, `Older` → receive newer, `Concurrent` → `Conflict`, `Equal` with `DIFFERENT` content → `Conflict` ("same version, different content": an unversioned edit) |

*Send newer*: `Upload`, unless local is evicted (→ `Conflict` "local bytes evicted") or remote is
evicted (→ nothing). *Receive newer* mirrors it.

Either side may evict in Mirror once both hold the same bytes under the same version; from there it
never conflicts unless a history it missed makes the other side strictly newer or concurrent.

### 6.2. One-way (AutoUpload, Offload)

Never plans `Download`, `DeleteLocal` or `Conflict`: the local side always wins.

| L | R | Decision |
|---|---|----------|
| - | any | nothing (remote-only files are the remote's business) |
| P | - | `Upload`, unless `skipModifiedBefore` and mtime is older |
| P | D | `Upload` under the dominating version ("deleted remotely, kept locally") |
| P | E | nothing (only this side evicts) |
| P | P | `SAME` → merge if diverged, else evict if due; `UNKNOWN` → `ComputeHash`; `DIFFERENT` → `Upload` under the dominating version (overwrites a remote edit) |
| D | P | `DeleteRemote` under the dominating version, only if `propagateDeletions` |
| D | D | merge if diverged |
| D | -/E | nothing |
| E | any | nothing (bytes live on the remote alone) |

Eviction due (Offload): the policy's criteria, combined with `All` or `Any`
([sources §3](sources.md#3-modes)); ages are measured against the snapshot's `now`, last access from
the index ([indexing §2](indexing.md#2-the-index-row)). A file with an unknown last access is never
due by `NotAccessedForDays`.

### 6.3. Hosted (Host mode, initiator = cache)

| L | R | Decision |
|---|---|----------|
| - | any | nothing (not cached: fetched on demand) |
| P | - | `Upload` |
| P | E | nothing |
| P | D | `Newer` → `Upload`; `Older` → hash-before-delete, then `DeleteLocal`; else `Conflict` |
| P | P | `SAME` → merge if diverged, else `EvictLocal` if evictable; `UNKNOWN` → `ComputeHash`; `DIFFERENT` → `Newer` `Upload`, `Older` `Download`, else `Conflict` |
| D | P | `Newer` → `DeleteRemote`; `Older` → nothing (stale cache entry); else `Conflict` |
| D | D | merge if diverged |
| D | E/- | nothing |
| E | D | `DeleteLocal` (eviction followed a confirmed copy, so nothing of ours is lost) |
| E | other | nothing |

Note: in Host mode every cached copy the host confirmably holds is evicted right away; the cache is
effectively "fetched on demand, expires after a TTL" ([eviction](eviction.md)).

## 7. File limits

Each device's own limits (`maxFiles`, `maxTotalSize`, both optional) apply to what **it receives**;
what it sends is the peer's call.

Planning side: `Download`s that would take this device past its limits are dropped from the runnable
set and counted as skipped - updates of held files first (a held file going stale is worse than a new
one waiting), newest first within each group; a held file's update is charged only its growth. Uploads
pass untouched: the receiver checks its own limits on `Init` and answers `OverLimit`, which the pass
also counts as skipped, not failed ([transfers §4.1](transfers.md#41-admission-init)).

## 8. Action guard

The last check before bytes are touched; any refusal is a strategy bug and the action is skipped:

| Action        | Refused when                                                                    |
|---------------|---------------------------------------------------------------------------------|
| any           | this device does not drive the source                                           |
| `Upload`      | local not `Present`                                                             |
| `Download`    | remote not `Present`                                                            |
| `EvictLocal`  | not `Present`, pinned, or not hashed (no copy can be confirmed)                 |
| `MergeVersion`| only one side deleted, or content not known to match                            |
| `Conflict`    | no side can be kept (both evicted)                                              |
| `Move*`       | a side has no bytes, or hashes not known to match                               |

Beyond the guard, every executor re-reads the index row and refuses if the file changed since
planning (version, hash or state) - see the operation descriptions in [transfers](transfers.md),
[eviction](eviction.md) and [sync pass §5](sync-pass.md#5-executing-actions).
