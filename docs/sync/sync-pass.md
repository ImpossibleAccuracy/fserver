# The sync pass

> How a pass is triggered, how two devices agree who runs it, what one pass does end to end, and how
> the other device serves it.
>
> The decision step inside a pass: [planning](planning.md). Message formats of file operations and
> uploads: [transfers](transfers.md).

## 1. Model

A **pass** brings one source on both devices into the state its mode prescribes. It is run by the
device that drives the source, under a **lease** the peer granted, and is bidirectional: one pass by
either side covers what a pass by the other would have done. The peer answers requests during the
pass; it plans nothing.

## 2. Triggers

There is no timer inside the engine - a host that wants periodic passes schedules its own job.

| Trigger                                               | Scope                          | If a pass is already running |
|-------------------------------------------------------|--------------------------------|------------------------------|
| host `runSync()`                                      | all sources                    | waits                        |
| host `runSync(source, force)` (user tapped "sync")    | one source; `force` ignores Wi-Fi/charging constraints | waits |
| source accepted / settings changed / source removed   | all sources                    | skipped                      |
| **auto-sync**: a device with an active source appears among visible devices (discovered or connected), or the network changes | that device's sources | skipped |
| peer's `RequestSync(source)` (a one-way follower asks the initiator) | one source        | coalesced: dropped while an earlier request for it has not started |
| user conflict decision                                | one source                     | waits                        |
| user evicts files by hand                             | one source, forced, then the eviction under the same lease | waits |

Runs are serialized by one runner mutex per process. Within a run, sources are processed one after
another; a failure of one does not stop the others.

## 3. The lease

Both devices may start a pass at the same moment (a timer here, a file change there). Each plans from
a snapshot of both indexes, so two concurrent passes plan against state the other is already
rewriting. The lease makes one of them skip - **skip, never queue**.

### 3.1. Protocol

```
runner                                              peer
  registry.beginAcquire(source) → leaseId           (local claim: Acquiring)
  ── AcquireSyncLease.Request {sourceId, leaseId, syncMode, metadata?} ──▶
                                                    authorize source (sources §6)
                                                    reconcile mode (§3.2)
                                                    check peerDrivesSync
                                                    registry.grantToPeer(...)
  ◀── Granted {leaseId, metadata?} | Denied {reason} | Outdated {syncMode} | Inactive {reason}
  registry.confirmLocal → Local                     (holds Remote lease, TTL 30 min)
  … pass …
  ── ReleaseLease {sourceId, leaseId, failure?} ──▶ (fire-and-forget)
                                                    registry.releaseFromPeer
```

Registry state per source (in memory, per process): `none | Acquiring(id) | Local(id) |
Remote(peer, id, expiresAt)`.

Granting rules on the answering side:

| Held here         | Grant?                                                                                   |
|-------------------|------------------------------------------------------------------------------------------|
| nothing           | yes                                                                                      |
| `Acquiring` (our own request in flight) | **only if the peer's device id < ours** - both sides reach the same verdict without another round trip, no livelock |
| `Local`           | no                                                                                       |
| `Remote` by the same peer | yes (idempotent - a re-send after a reconnect is not a second device)            |
| `Remote` expired  | treated as none (backstop for a peer that died without its session ending)              |

After a `Granted`, the requester re-checks its own claim: if a peer request with a winning id took
the source over meanwhile, it hands the grant straight back and skips.

Answers:

- `Denied` → skip this source (the peer is syncing it, it is unknown there, or the modes have
  different types, or the source is driven from here);
- `Outdated(mode)` → this side is a follower with stale mode settings: adopt the initiator's mode and
  retry once;
- `Inactive(reason)` → the peer removed/disabled its half: mark this half `Disabled(reason)` and
  journal it.

**Renewal while running**: the holder re-sends `AcquireSyncLease.Request` with the **same lease id**
every TTL/3 (10 min) for as long as its pass runs; the peer re-grants its holder idempotently and
extends `expiresAt`. A pass of any length therefore keeps its lease, and a holder that died loses it
within one TTL.

Release is best effort and carries the pass outcome (`failure` reason or none): the peer shows a
"remote pass failed" entry, or clears its own "pass failed" issue on success. A lease is also freed
when the session it was granted on ends (every lease of that peer) and by the TTL.

### 3.2. Mode reconciliation

Both halves must run the same mode before either plans:

| Peer's mode vs ours        | Verdict                                                             |
|----------------------------|---------------------------------------------------------------------|
| equal                      | agreed                                                              |
| different **type**         | never reconciled → `Denied("different modes")`                      |
| same type, we are initiator | the peer is outdated → `Outdated(our mode)`                        |
| same type, we are follower | adopt the peer's settings - **only after the lease is granted** (a denial keeps the old mode for the pass running here) |

## 4. One pass, step by step

`process(source, force)`:

1. **Status**: not `Active` → refresh the local index only, stop.
2. **Device constraints** (unless forced): Wi-Fi/Ethernet required, charging required.
3. **Not the driver** (one-way follower) → send `RequestSync` to the peer, stop.
4. **Acquire the lease** (§3). Skipped → stop.
5. **Measure peer clock** ([versioning §5](versioning.md#5-peer-clock-skew)) - before any conflict
   can be resolved.
6. **Scan once** ([indexing](indexing.md)).
7. **Rounds** (up to 3 after the first):
   1. fetch the peer's index (`FetchFiles` → the peer refreshes its own index and answers it; written
      through to the remote index cache; max HLC fed to the clock);
   2. plan ([planning](planning.md));
   3. drop stored conflict decisions the plan no longer needs ([conflicts §3](conflicts.md#3-ask));
   4. apply this device's file limits;
   5. run actions: `ComputeHash` always; an action on a file still waiting for a hash is held back to
      the next round; **at most one action per file per pass** (a later round re-plans from an index
      a transfer has not written back yet);
   6. stop when the plan is empty or nothing was waiting for a hash; otherwise re-read the local index
      (not the disk) and plan again. Still unhashed after the last round → pass error.
8. **Outcome**: all actions OK → `markSynced`, journal `PassCompleted` (tally: sent, received,
   deleted here/on peer, moved, evicted, skipped), solve `PassFailed`, solve held-conflict issues for
   files no longer in conflict. Any failed action → the pass fails with every error attached; journal
   `PassFailed` (+ `SealedFilesUnreadable` if the cause was a missing key/unknown cipher/corruption).
9. Optional `whileHeld` block (used by "evict by hand"): re-fetch the peer index and run under the
   same lease - the peer can change nothing meanwhile.
10. **Release the lease**, then **publish** this device's full index to the peer (`PublishIndex`,
    fire-and-forget, only over an existing session - never dials just for this).

After a whole run: garbage collection, encryption migration, and re-sending setup asks still
`Pending` ([sources §4](sources.md#4-setup-exchange)).

## 5. Executing actions

Each action is checked by the guard ([planning §8](planning.md#8-action-guard)) and dispatched:

| Action          | Here                                                    | On the peer (`OperationWithConfirmation`) |
|-----------------|---------------------------------------------------------|-------------------------------------------|
| `Upload`        | push bytes ([transfers](transfers.md)); record the hash if it was unknown; upsert the remote cache | receives, verifies, places, indexes |
| `Download`      | ask the peer to push the file back                      | `File.Download` → it uploads to us        |
| `DeleteRemote`  | -                                                       | `File.Delete(version?)`                   |
| `DeleteLocal`   | delete bytes + record the peer's version, refused if the row changed since planning (state, hash, version) | - |
| `MoveRemote`    | -                                                       | `File.Move(expected hash, target, deletedVersion)` |
| `MoveLocal`     | rename via `place`; refused if the source changed or something sits at the target | - |
| `EvictLocal`    | [eviction](eviction.md)                                 | -                                         |
| `ComputeHash`   | hash local side if present                              | `File.Hash` if remote side present        |
| `MergeVersion`  | `adoptVersion` locally if not already there             | `File.AdoptVersion(version, expected)`    |
| `Conflict`      | [conflicts](conflicts.md)                               | -                                         |

`OperationWithConfirmation.Request {operationId, instance}` is answered `Completed` or
`Failed(reason)`. The peer checks per operation: `Hash`/`Download` always; `Delete`/`Move` only if it
accepts peer writes; `AdoptVersion` only if the peer drives the source.

An `OverLimit` refusal from the peer is a skip, not a failure.

## 6. Link loss during a pass

If the session drops under any step (`SessionClosed`, `SessionLinkLost`, transport error, no route,
unreachable), the step is retried over a **renewed lease**: dial again, re-send `AcquireSyncLease`
with the **same lease id** (the peer re-grants its holder idempotently), continue. At most 2 renewals
per step; otherwise the pass aborts with what failed so far. Uploads resume from the receiver's
checkpoint ([transfers §5](transfers.md#5-staging-and-resume)); the one-action-per-file rule means
nothing is executed twice in one pass.

## 7. The answering side

`startServing()` attaches one serving loop to every session (inbound or dialled) as it appears; a
reconnect is a new session object and replaces the old loop.

Per session:

- **Order-sensitive messages are handled inline, in arrival order**: all upload messages
  (`Init`, chunks, `Status`, `Complete`, `Abandon`) and lease `Request`/`ReleaseLease`. Concurrent
  dispatch would reorder them; a release overtaking its acquire strands the lease until the TTL.
- **Everything else** runs concurrently, at most **5 per peer**. Over the cap a request is refused
  immediately (`Failed("Receiver busy")` / `FetchFiles.Failed`) - the collector must never block,
  because the network layer drops frames while it is not drained.
- Upload chunks are handed to per-upload writers, never written on the collector
  ([transfers §4](transfers.md#4-receiver)).
- When the session ends: all leases that peer held are released, open uploads are parked in staging.
- On every new session the peer's reachability is recorded and owed one-shot transfers are resumed.

| Message                         | Handler                                                                    |
|---------------------------------|----------------------------------------------------------------------------|
| `FetchFiles.Request(source)`    | authorize, refresh the local index, answer `FilesList` / `Failed`          |
| `PublishIndex(source, files)`   | authorize, replace the remote index cache, feed max HLC to the clock       |
| `RequestSync(source)`           | authorize; if this side drives it, queue a pass (coalesced)                |
| `AcquireSyncLease.*`            | §3                                                                         |
| `ClockProbe.Request`            | answer the reading, record the one-way offset                              |
| `ConfigureSource.*`             | [sources §4](sources.md#4-setup-exchange)                                  |
| `Upload*`, `UploadChunk`        | [transfers](transfers.md)                                                  |
| `OperationWithConfirmation`     | §5                                                                         |
| `OneShot.*`                     | [one-shot transfers](../services/one-shot-transfers.md)                    |
