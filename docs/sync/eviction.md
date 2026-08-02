# Eviction, pinning and fetch on demand

> Freeing local bytes while keeping the file in the set: when it is allowed, how the copy on the peer
> is confirmed, placeholders, pins, and getting the bytes back.

## 1. Evict is not delete

Eviction frees this device's bytes of a file **the peer holds**. The file stays in the set:

- the index row becomes `Evicted(at)`; **the version does not change**;
- it is reported to the peer as `Evicted`, never `Deleted`, so it never propagates as a user
  deletion (ToR §3.4);
- an evicted record never sends, is never "refilled" by a plan, never turns into a deletion, and
  cannot win a conflict;
- a scan that does not find an evicted file on disk leaves it `Evicted`.

Collapsing `Evicted` into `Deleted` anywhere - storage, wire, planner - loses user data.

## 2. Who evicts, when

| Path                 | Where allowed                              | Criterion |
|----------------------|--------------------------------------------|-----------|
| Plan (`EvictLocal`)  | Offload initiator ([planning §6.2](planning.md#62-one-way-autoupload-offload)) | file confirmed on the follower **and** due by the policy ([sources §3](sources.md#3-modes)) |
| Plan (`EvictLocal`)  | Host initiator ([planning §6.3](planning.md#63-hosted-host-mode-initiator--cache)) | every confirmed, evictable cached copy |
| By hand              | any source this device drives, `Active`    | the user selected files |
| TTL                  | sources that evict locally                 | a copy fetched on demand older than 1 day (garbage collection) |

**Pinned** files are never evicted by a plan or a TTL. Pinning is allowed where the source evicts
locally (`pinsFiles`). Unpinning a fetched copy restarts its TTL.

Plans never evict fetched copies - only the TTL does - so the user gets a full day with a file they
opened.

## 3. Confirmation - when eviction is allowed

Right before freeing bytes, under the current index and the peer's last reported index, a file is
evictable only if none of these refusals applies:

| Refusal       | Condition                                                                                   |
|---------------|---------------------------------------------------------------------------------------------|
| `NotHere`     | not `Present` here                                                                          |
| `Pinned`      | pinned                                                                                      |
| `Unverified`  | our hash unknown or stale, or the peer's hash unknown                                       |
| `NotOnPeer`   | the peer has no row or its row is not `Present`                                             |
| `PeerDiffers` | the peer's hash differs from ours                                                           |
| `Unmerged`    | same bytes but different version vectors - evicting now would conflict with the next edit; a pass merges them first |

Additionally the row must still hold the hash the plan/user saw (`expected`); otherwise "changed since
planned". This is ToR §3.4's "only after the copy on the backup is confirmed by content hash".

Eviction by hand runs **inside a forced pass, under its lease**, against a freshly fetched peer index:
the peer cannot evict the same file at the same moment (which would leave no bytes anywhere).

## 4. Fetch on demand

A file this device lacks may be fetched by the user (opening a placeholder) where
`fetchesOnDemand` (Mirror; Offload/Host initiator) or the file was evicted by hand here. The fetch is
an ordinary download into the source itself ([transfers §6](transfers.md#6-downloads)), so a pass
afterwards sees both sides equal. Where the source evicts locally, the copy is marked
`fetchedAt = now` and expires after the TTL.

## 5. Placeholders and previews

The UI keeps showing an evicted file from index metadata (path, size, mtime, states on both sides).
Just before eviction frees the bytes, the optional host hook `EvictionPreviewer.capture(file)` may read
them to keep a preview (thumbnail, frame, cover). Best effort: a failure or a 30 s timeout is logged
and eviction proceeds without a preview. Previews are stored in plain form by the host
([at-rest encryption §6.4](../files/at-rest-encryption.md#64-plaintext-leftovers)).

A placeholder is flagged **lost on peer** when it is evicted here and the peer no longer holds the
same bytes (no row, or a different hash) - the user should know the file can no longer be fetched.

## 6. Execution

`evict(source, file, expected)`:

1. re-check row hash = `expected` and the refusals of §3;
2. capture the preview (cancellable);
3. **non-cancellable**: delete the bytes (already gone counts as deleted), then
   `recordEvicted` (state only).

A backend that refuses the delete leaves the file `Present`.
