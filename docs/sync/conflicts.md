# Conflict resolution

> What happens to a `Conflict` the planner emits: last-write-wins, or holding it for the user and
> carrying out their decision.
>
> When the planner calls something a conflict: [planning §6](planning.md#6-decision-tables).
> Version vectors and HLC: [versioning](versioning.md).

## 1. What reaches this step

A conflict is a pair of records the planner refuses to order:

- **concurrent** vectors with different content (both sides edited);
- one side deleted, the other edited, vectors `Equal` or `Concurrent`;
- **equal** vectors with different content (an edit one side never versioned);
- the newer side has no bytes (evicted) - the change cannot be sent.

Same content under different vectors is **never** a conflict (it is a merge). One-way modes never
produce conflicts: the initiator always wins.

There is no persistent "in conflict" state. A conflict *is* a pair of concurrent versions; it
disappears as soon as a version newer than both lands on either side.

## 2. Choosing the resolution

The resolution is a setting of the two-way modes, `Mirror` and `Host`: `Ask` (default) or
`LastWriteWins`. One-way modes never conflict.

| Setting                   | Resolution                                                   |
|---------------------------|--------------------------------------------------------------|
| `Ask` (default)           | Ask                                                          |
| `LastWriteWins`           | LWW, **unless the peer's clock is skewed** → Ask (§4)        |

**A side without bytes.** When one side of a conflict is evicted, the side with bytes is the only
possible outcome: LWW picks it, Ask offers only it (and resolves without a prompt when the user has
nothing to choose). Either way the conflict is gone after that pass - both sides hold the merged
version.

## 3. Ask

The user's file does not change without their decision.

1. **The conflict is held**: no bytes move, versions are not merged, each side keeps its own version
   under the original path. A journal issue `ConflictHeld(source, file)` is raised.
2. **Each side finds the conflict on its own** - by running the same planning over its own index and
   the peer's last reported index (the remote index cache, refreshed by every fetch and every
   `PublishIndex` the peer sends after its pass). Nothing about a conflict is ever sent.
3. **The user decides on either side**:
   - **keep mine** - transfer as in LWW, winner = this side;
   - **keep theirs** - same, winner = the peer;
   - **keep both** - this side's bytes are first copied next to the file as a new file
     `<name> (<this device's name>).<ext>` (`… (<name> 2).<ext>` etc. if taken; a leading dot is a
     hidden file, not an extension), then as "keep theirs". Only when both sides have bytes.

   A side whose bytes were evicted cannot be chosen.
4. **The decision is bound to the versions the user saw**:
   `ConflictDecision { source, file, choice, local: (hlc, origin)?, remote: (hlc, origin)?, decidedAt }`.
   Every edit issues a fresh `(hlc, origin)` pair and a merge never does, so the pair names a version.
   The decision is stored and a pass over the source is started.
5. **At the pass**, for each `Conflict` action:
   - no decision → keep holding (re-raise the issue);
   - either side's `(hlc, origin)` differs from what the user saw → drop the decision, journal
     `ConflictDecisionDropped(SideChanged)`, hold again - the user never saw what it would overwrite;
   - the choice is no longer available (a side was evicted since - eviction is not a version, so the
     check above misses it) → drop (`ChoiceUnavailable`), hold;
   - otherwise carry it out (§5). For "keep both" the stored decision is first rewritten to "keep
     theirs" once the copy exists, so a retry after a failed transfer does not make a second copy.
   On success the decision is removed, `ConflictResolved` is journaled and the held issue solved.
6. **Settled decisions** are dropped at the start of every pass: any stored decision for a file the
   plan no longer reports as a conflict (and is not waiting on a hash) - the peer's decision got
   there first, or someone edited past it. If the source no longer asks (LWW without skew), all its
   stored decisions are dropped.

The UI's list of pending conflicts = held conflicts of active Ask sources, minus those with a
decision that still covers both sides' current versions.

## 4. Clock skew fallback

While the last measured offset to the peer exceeds `MAX_DRIFT_MS`
([versioning §5](versioning.md#5-peer-clock-skew)), a `LastWriteWins` source behaves as
`Ask`: a winner picked by a wrong clock is unfair. Decisions made during the skew are dropped once the
clock is back to normal and LWW applies again.

## 5. Last write wins and the transfer

**Winner**, deterministic so both sides would pick the same one independently:

1. a side without bytes (evicted) cannot win;
2. both versions equal (same vector, HLC and origin) → the device with the greater id wins;
3. a side without a version loses;
4. otherwise the greater `(hlc, originDevice)` wins.

**Transfer** - the same for LWW and for a user decision:

- merged version `M` = merge of both versions ([versioning §3](versioning.md#3-version-vector-operations));
- winner `Present`, local → `Upload` under `M`, then adopt `M` locally (expected = the hash just
  sent);
- winner `Present`, remote → `Download` under `M`, then ask the peer to adopt `M` (expected = the
  received hash);
- winner `Deleted` (loser not deleted) → delete on the loser's side under `M`, the winner adopts `M`
  (expected = deleted);
- winner `Evicted` → nothing (filtered out by the rules above).

Adopting is refused if the winner's file changed after planning (hash or state no longer match), so
both sides converge in this pass or the change is re-planned next time.

**The losing content is not kept** under LWW. A local recovery bin is future work (ToR §3.4).
