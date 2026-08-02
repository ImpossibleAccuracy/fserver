# Versioning: version vectors and HLC

> How file versions are represented and ordered, how the hybrid logical clock runs, and how peer clock
> skew is measured.
>
> When a version is issued: [indexing](indexing.md). How concurrent versions are resolved:
> [conflicts](conflicts.md).

## 1. Principles

- **Order comes from the version vector, never from time.** Time (HLC) only picks the winner of a
  genuine conflict. Raw mtime never orders anything.
- **Deletion is a version** (tombstone with a bumped vector).
- **Eviction is not a version** - the vector is unchanged, only the row state changes.
- **Encryption changes are not versions** - content did not change.
- **Accepting a peer's version records the peer's version** - no edit of our own is added.

## 2. A version

```
Version {
    vector:       { deviceId -> edit count }    // zero counters never stored, so equal histories are equal values
    hlc:          i64                           // packed HLC reading of when the version was made
    originDevice: deviceId                      // who made it
}
```

Part of the protocol (ToR §4.4): sent in every index listing and with every operation that changes a
file on the other side (upload, download, delete, move, adopt). Wire form:
`{"vector": {...}, "hlc": <i64>, "originDevice": "..."}`. A zero counter received from a peer is
dropped, not refused.

A file with no version (`null`) reads as the empty vector: any versioned record is newer than it.

## 3. Version vector operations

- **bump(d)**: `counters[d] += 1` - this device makes a new version.
- **merge(a, b)**: component-wise max.
- **compare(a, b)** over the union of device ids:

| `a[d] >= b[d]` for all d | `a[d] <= b[d]` for all d | Result       |
|:------------------------:|:------------------------:|--------------|
| ✓                        | ✓                        | `Equal`      |
| ✓                        |                          | `Newer`      |
|                          | ✓                        | `Older`      |
|                          |                          | `Concurrent` - each side has an edit the other has not seen: the only real conflict |

**Merging two versions** (same content under different histories, or a conflict resolution):
vector = merge of both; `hlc` and `originDevice` from the later of the two by `(hlc, originDevice)`.
No separate bump is needed: both sides compute the same value independently.

**Issuing a local version**: `vector = previous.bump(localDeviceId)`, `hlc = clock.tick()`,
`originDevice = localDeviceId`.

## 4. Hybrid logical clock

A reading is `(physical ms, logical counter)` packed into a non-negative i64: **48 bits of
milliseconds, 16 bits of counter**. The packed integer compares exactly like the reading, so it is
stored and sent as a plain integer.

- **Local event** (`tick`): if wall time moved past the last reading → `(wall, 0)`; else
  `(last.ms, last.counter + 1)`. Readings never go back, even if the wall clock does.
- **Counter overflow** (65535) carries into the milliseconds: `(ms + 1, 0)`.
- **Receiving a peer reading** (`receive`): standard HLC - `ms = max(last.ms, remote.ms, wall)`;
  counter from whichever reading(s) gave the max, +1; `(wall, 0)` if the wall alone is ahead.
  If the remote reading is ahead of our wall clock by more than `MAX_DRIFT_MS`, its physical part is
  **not adopted** (treated as the wall time). This protects only our own clock; the warning comes
  from §5.
- **When remote readings are received**: on every index fetched from or published by the peer, the
  maximum HLC in the listing is fed to `receive`.
- **Batches**: one scan's changes get consecutive readings from a single locked `ticks(n)` call,
  persisted once.
- **Persistence**: the last reading is saved on every change, so readings keep growing across
  restarts.

## 5. Peer clock skew

A version's HLC is a record of the past, not a measurement of a clock: skew shows up late, on the
wrong device, or never. So before every pass the leading device measures the peer's clock NTP-style:

```
initiator                                   peer
  t0 = now
  ── ClockProbe.Request {sentAt = t0} ──▶   r1 = now (on receipt)
                                            records one-way offset (t0 - r1) for the initiator
  ◀── ClockProbe.Reading {receivedAt = r1, repliedAt = r2} ──
  t3 = now
  offset = ((r1 - t0) + (r2 - t3)) / 2      // peer clock minus ours
```

The answering side gets a one-way estimate (off by link latency - far below the drift that matters),
so **both** sides learn about skew.

- **Skewed** = `|offset| > MAX_DRIFT_MS`, either direction. It raises a journal issue
  (`ClockSkewed`), solved by the next normal measurement.
- While a peer is skewed, a source configured for LWW behaves as **Ask**
  ([conflicts §4](conflicts.md#4-clock-skew-fallback)): a winner picked by a wrong clock is unfair.
- Offsets are kept **in memory only**; every pass measures again. As soon as the offset is normal,
  LWW is back.

## 6. Constants

| Parameter      | Value                      |
|----------------|----------------------------|
| `MAX_DRIFT_MS` | 60 000                     |
| HLC packing    | 48 bits ms, 16 bits counter |
