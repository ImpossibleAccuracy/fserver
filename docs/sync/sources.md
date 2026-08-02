# Sources

> What a source is, how two devices agree to sync one, what each mode lets each end do, and how a
> source is authorized, changed and removed.

## 1. Definition

A **source** is one location on this device paired with **one** peer device, under one mode. Both
devices hold a record under the **same source id** (chosen by the device that created it); a source
known on one side only syncs nothing.

```
SourceEntry {
    id            // shared by both halves
    deviceId      // the one peer this source syncs with - also its access control (§6)
    location      // where the files are here (never sent)
    syncMode      // Mirror | AutoUpload | Offload | Host, with settings
    preferences   // this device's own: Wi-Fi/charging constraints, file limits, encryption policy
    role          // Initiator | Follower
    status        // Pending | Active | Disabled(reason)
    label, createdAt, lastSyncedAt
}
```

`preferences` are never sent and never reconciled: each device decides when and how much it takes in
and whether it encrypts its copy.

## 2. Roles and status

- **Initiator** - registered the source and asked the peer to host it. One-way modes flow *from* it.
- **Follower** - accepted the ask and stores what arrives in a location it picked.

| Status        | Meaning                                                                              | Passes run? |
|---------------|--------------------------------------------------------------------------------------|:-----------:|
| `Pending`     | registered here, waiting for the peer's user; this side still answers the peer       | no (index is refreshed) |
| `Active`      | both sides hold it                                                                   | yes         |
| `Disabled(r)` | the peer refused, or dropped its half later; kept so the next pass does not re-ask and the user sees why | no |

## 3. Modes

| Mode         | ToR term | Flow                                                                   | Location must be |
|--------------|----------|------------------------------------------------------------------------|------------------|
| `Mirror(conflictResolution)` | Sync | both ways; same set on both sides; `Ask` or `LastWriteWins` | hostable          |
| `AutoUpload(ignoreFilesBefore?)` | (not in ToR) | initiator → follower; deletions propagate; follower changes never come back | any selectable |
| `Offload(policy)` | Offload | initiator → follower; deletions **do not** propagate; local bytes evicted once the follower holds them | any selectable |
| `Host(conflictResolution)` | Host | files live on the follower; the initiator keeps an on-demand cache; `Ask` or `LastWriteWins` | hostable |

`Offload.policy` = one or more criteria and how they combine (`All` / `Any`), ToR §3.4 "by age
and/or by last access":

| Criterion                | Due when                                                         |
|--------------------------|------------------------------------------------------------------|
| `NotModifiedForDays(n)`  | last modification older than `n` days                            |
| `NotAccessedForDays(n)`  | last access through the core older than `n` days (LRU)           |
| `LargerThanBytes(n)`     | file larger than `n` bytes                                       |

Pinned files are never due.

### 3.1. What each end may do

Derived from mode + role; these rules are checked on both the asking and the answering side.

| Rule                 | True when                                                       | Effect |
|----------------------|-----------------------------------------------------------------|--------|
| `drivesSync`         | Mirror, or Initiator                                            | runs passes and file actions; may edit files locally through the core |
| `peerDrivesSync`     | Mirror, or Follower                                             | grants the peer a lease; honors its version adoptions |
| `acceptsPeerWrites`  | = `peerDrivesSync`                                              | the peer may push, overwrite, delete, move files here |
| `evictsLocally`      | Initiator in Offload or Host                                    | plan may evict; fetched copies expire |
| `pinsFiles`          | `evictsLocally` and Active                                      | user may pin |
| `evictsByHand`       | `drivesSync` and Active                                         | user may evict files the peer confirmably holds |
| `fetchesOnDemand`    | Mirror; Initiator in Offload/Host; never AutoUpload             | missing files may be fetched by the user |
| `sharesMetadata` / `receivesMetadata` | the side a pass runs against reports its half to the side running it | informational metadata exchange (§7) |

A follower of a one-way mode never runs a pass: when asked to sync it sends `RequestSync` to the
initiator instead ([sync pass §2](sync-pass.md#2-triggers)).

## 4. Setup exchange

```
initiator                                           follower
  addSource(location, mode, deviceId, label)
  status = Pending, journal "added"
  initial index scan (background)
  ── ConfigureSource.Request {id, label, mode, metadata} ─▶
                                                     park as IncomingSourceRequest
                                                     (persisted; journal "requested")
                                                     … user decides, possibly hours later …
                                                     accept(location = Internal(bucket=id) default)
                                                       → SourceEntry(role=Follower, status=Active)
  ◀── ConfigureSource.Decision {id, accepted=true} ──
  status = Active, journal "accepted by peer"
```

Rules:

- The ask carries the source size, so it is sent **after** an initial scan (a failed scan still
  asks). Sending is best effort; every round of passes re-sends asks still `Pending`.
- The mode a follower registers is the initiator's mode, carried unchanged.
- **Re-asks are answered, not re-parked**: if the source already exists here for this peer, the stored
  verdict is sent again (`Disabled` → refused with its reason); if this device removed or refused it
  (tombstone for the same peer), the answer is a refusal - parking it again would ask the user to undo
  their own decision. A re-ask of a parked request refreshes it but keeps its place in the queue.
- An ask for an id that exists for **another** device is ignored.
- **Reject** records a location-less tombstone, drops the request and answers `accepted=false`.
  The initiator then marks its half `Disabled(reason)` - kept, not deleted.
- Decisions are accepted only from the device the source syncs with.
- **Lost acceptance recovery**: if the follower's `Decision` never arrived, its first lease/index
  request does the job - a `Pending` source asked about by its own peer is switched to `Active`
  (the peer would not be asking about a source it had not registered).

Constraints at registration: the mode must be available for the location (§3), one source per
`(mode, location)` (two would sync the same files twice), the encryption policy must be supported by
the location and cipher registry.

## 5. Removal

`removeSource(id)`:

1. cancels a still-running initial scan/ask (it would write rows back and ask for a gone source);
2. deletes the record, its local and remote index and metadata, leaving a **tombstone**
   `(id, peer, removedAt, lastLocation)`;
3. solves the source's open journal issues;
4. tells the peer, best effort: `ConfigureSource.Decision {id, accepted = false, reason = "Source was
   removed on the other device"}` - the peer disables its half.

**Nothing on disk is touched** - unregistering is neither eviction nor deletion.

If that message is lost, the tombstone answers the peer's next lease request with
`Inactive("Source was removed on the other device")`, with the same effect.

## 6. Authorization (answering side)

Every source id a peer sends was chosen by that peer, so without a check any authenticated device
could list, overwrite or delete any source. **Every** handler resolves the id through one function:

| Record here                                   | Result for the asking peer                          |
|-----------------------------------------------|-----------------------------------------------------|
| exists, `deviceId` = peer, `Active`           | servable                                            |
| exists, `deviceId` = peer, `Pending`          | switched to `Active`, servable (§4)                 |
| exists, `deviceId` = peer, `Disabled(r)`      | gone (r)                                            |
| exists for another device                     | unknown                                             |
| tombstone for this peer                       | gone ("removed")                                    |
| nothing                                       | unknown                                             |

"Unknown" is deliberately the same answer for a miss and for someone else's source: an unrelated
peer learns nothing about what exists. Mode rules (§3.1) are checked *after* authorization, per
operation.

## 7. Changing a source

- `updateSource` may change mode **settings** and preferences, never the mode **type** (that would
  reinterpret an index written under other rules). Only the initiator may change the mode; a
  follower may change only its preferences.
- The follower picks up a new mode at the next lease: the lease request carries the requester's mode
  ([sync pass §3.2](sync-pass.md#32-mode-reconciliation)).
- A policy/encryption change triggers a pass and an encryption migration.

**Metadata** (informational, never read by engine decisions): each side's storage kind (folder / app
storage / media), readable folder path, file count, bytes and percentage of its own limits. Sent with
the setup request and with lease messages by the side a pass runs against; shown to the user.
